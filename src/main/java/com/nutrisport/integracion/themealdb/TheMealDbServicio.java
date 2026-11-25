package com.nutrisport.integracion.themealdb;

import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.LlamadaProveedor;
import com.nutrisport.integracion.comun.OperacionProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente de TheMealDB (docs/API_INTEGRATIONS.md §4). Solo usa los endpoints de la API v1 de §4.1, con la clave en
 * la ruta: {@code {baseUrl}/{apiKey}/search.php}. La clave de prueba "1" solo vale para desarrollo y uso
 * educativo.
 * <p>
 * Las reglas de cada llamada (cupo propio de 30/min, disponibilidad, un reintento ante timeout o 5xx, caída de
 * 60 s, 429 y 503 con {@code Retry-After}) son las comunes de {@link LlamadaProveedor}; si no se puede llamar,
 * lanza {@link ProveedorNoDisponibleException}. Sin resultados, TheMealDB responde {@code {"meals": null}}: lista
 * vacía. Los filtros solo devuelven id, nombre e imagen: para los ingredientes hay que pedir el detalle.
 * <p>
 * Las recetas importadas se guardan en PostgreSQL (§6.5); aquí solo se guardan en caché los filtros y las listas
 * (24 h) y las categorías (7 días).
 */
@Service
public class TheMealDbServicio {

    static final int LONGITUD_MINIMA_NOMBRE = 2;
    static final int LONGITUD_MAXIMA = 60;

    private static final Pattern ID_MEAL = Pattern.compile("\\d{1,10}");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    private final RestClient cliente;
    private final boolean configurado;
    private final LlamadaProveedor llamada;

    @Autowired
    public TheMealDbServicio(RestClient.Builder constructor, IntegracionProperties propiedades,
            LimitadorProveedor limitador, EstadoProveedor estadoProveedor, Clock reloj) {
        this(constructor, propiedades, limitador, estadoProveedor, reloj, LlamadaProveedor.ESPERA_REINTENTO);
    }

    /** Para los tests: permite acortar la espera antes del reintento. */
    TheMealDbServicio(RestClient.Builder constructor, IntegracionProperties propiedades, LimitadorProveedor limitador,
            EstadoProveedor estadoProveedor, Clock reloj, Duration esperaReintento) {
        IntegracionProperties.Proveedor mealDb = propiedades.de(ProveedorExterno.THEMEALDB);
        this.llamada = new LlamadaProveedor(ProveedorExterno.THEMEALDB, limitador, estadoProveedor, reloj,
                esperaReintento);
        this.configurado = mealDb.baseUrl() != null && mealDb.apiKey() != null;
        RestClient.Builder constructorCliente = constructor.clone()
                .requestFactory(LlamadaProveedor.fabricaConTimeouts(mealDb));
        if (configurado) {
            constructorCliente.baseUrl(sinBarraFinal(mealDb.baseUrl()) + "/" + mealDb.apiKey());
        }
        this.cliente = constructorCliente.build();
    }

    // ---------------------------------------------------------------------------------------------
    // Recetas completas
    // ---------------------------------------------------------------------------------------------

    /** Recetas completas cuyo nombre contiene el texto ({@code search.php?s=}). */
    public List<RecetaExternaDto> buscarPorNombre(String nombre) {
        String limpio = validarTexto("s", nombre, LONGITUD_MINIMA_NOMBRE);
        return RespuestaMealDb.elementos(obtener(OperacionProveedor.BUSQUEDA, uri -> uri
                .path("/search.php").queryParam("s", "{s}").build(limpio)), "meals").stream()
                .map(RespuestaMealDb::aReceta)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Receta completa por su idMeal ({@code lookup.php?i=}); vacío si no existe. */
    public Optional<RecetaExternaDto> porId(String idMeal) {
        String limpio = idMeal == null ? "" : idMeal.strip();
        if (!ID_MEAL.matcher(limpio).matches()) {
            throw invalida("idMeal", "El identificador de TheMealDB debe ser numérico");
        }
        return RespuestaMealDb.elementos(obtener(OperacionProveedor.PRODUCTO, uri -> uri
                .path("/lookup.php").queryParam("i", "{i}").build(limpio)), "meals").stream()
                .map(RespuestaMealDb::aReceta)
                .filter(Objects::nonNull)
                .findFirst();
    }

    // ---------------------------------------------------------------------------------------------
    // Filtros (solo id, nombre e imagen)
    // ---------------------------------------------------------------------------------------------

    /** Recetas con ese ingrediente principal, en inglés ("chicken_breast" o "Chicken Breast"). */
    @Cacheable(cacheNames = CacheConfig.MEALDB_LISTAS,
            key = "T(com.nutrisport.integracion.themealdb.TheMealDbServicio).clave('i', #ingrediente)")
    public List<RecetaResumenExternaDto> filtrarPorIngrediente(String ingrediente) {
        return filtrar("i", ingrediente);
    }

    /** Recetas de una strCategory de TheMealDB ("Chicken"). */
    @Cacheable(cacheNames = CacheConfig.MEALDB_LISTAS,
            key = "T(com.nutrisport.integracion.themealdb.TheMealDbServicio).clave('c', #categoria)")
    public List<RecetaResumenExternaDto> filtrarPorCategoria(String categoria) {
        return filtrar("c", categoria);
    }

    /** Recetas de un área ("Moroccan"). */
    @Cacheable(cacheNames = CacheConfig.MEALDB_LISTAS,
            key = "T(com.nutrisport.integracion.themealdb.TheMealDbServicio).clave('a', #area)")
    public List<RecetaResumenExternaDto> filtrarPorArea(String area) {
        return filtrar("a", area);
    }

    // ---------------------------------------------------------------------------------------------
    // Listas
    // ---------------------------------------------------------------------------------------------

    /** Las categorías de TheMealDB ({@code categories.php}). */
    @Cacheable(cacheNames = CacheConfig.MEALDB_CATEGORIAS, key = "'categorias'")
    public List<CategoriaExternaDto> categorias() {
        return RespuestaMealDb.elementos(obtener(OperacionProveedor.CONSULTA, uri -> uri
                .path("/categories.php").build()), "categories").stream()
                .map(RespuestaMealDb::aCategoria)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Las áreas de TheMealDB ({@code list.php?a=list}), p. ej. "Moroccan". */
    @Cacheable(cacheNames = CacheConfig.MEALDB_LISTAS, key = "'areas'")
    public List<String> areas() {
        return RespuestaMealDb.textos(RespuestaMealDb.elementos(obtener(OperacionProveedor.CONSULTA, uri -> uri
                .path("/list.php").queryParam("a", "list").build()), "meals"), "strArea");
    }

    /** Clave de la caché de filtros: tipo de filtro y valor en minúsculas, para que no se mezclen entre sí. */
    public static String clave(String tipo, String valor) {
        return tipo + ":" + (valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private List<RecetaResumenExternaDto> filtrar(String parametro, String valor) {
        String limpio = validarTexto(parametro, valor, 1);
        return RespuestaMealDb.elementos(obtener(OperacionProveedor.BUSQUEDA, uri -> uri
                .path("/filter.php").queryParam(parametro, "{valor}").build(limpio)), "meals").stream()
                .map(RespuestaMealDb::aResumen)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> obtener(OperacionProveedor operacion, Function<UriBuilder, URI> uri) {
        if (!configurado) {
            throw llamada.sinConfiguracion("THEMEALDB_BASE_URL o THEMEALDB_API_KEY");
        }
        return llamada.obtener(operacion, cliente, uri);
    }

    private static String validarTexto(String campo, String valor, int minimo) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.length() < minimo || limpio.length() > LONGITUD_MAXIMA) {
            throw invalida(campo, "Debe tener entre " + minimo + " y " + LONGITUD_MAXIMA + " caracteres");
        }
        return limpio;
    }

    private static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
