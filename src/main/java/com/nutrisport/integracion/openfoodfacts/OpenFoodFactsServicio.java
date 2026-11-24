package com.nutrisport.integracion.openfoodfacts;

import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.OperacionProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente de Open Food Facts (docs/API_INTEGRATIONS.md §3 y §6). Solo usa los dos endpoints documentados:
 * <ul>
 * <li>producto por código: {@code GET {baseUrl}/api/v2/product/{codigo}.json?fields=...};</li>
 * <li>búsqueda por texto en Search-a-licious: {@code GET {searchUrl}/search?q=&page=&page_size=&fields=...}.</li>
 * </ul>
 * Cada petición lleva el User-Agent con contacto, y la de la API, la autenticación básica de staging si está
 * configurada (en desarrollo siempre se usa staging). La búsqueda va a otro dominio y no la lleva.
 * <p>
 * Antes de cada llamada comprueba la disponibilidad del proveedor y el cupo propio. Ante un timeout o un 5xx
 * reintenta una vez (consumiendo cupo, porque es otra llamada) y, si vuelve a fallar, marca el proveedor como
 * caído 60 s. Un 429 o un 503 no se reintentan: se respeta {@code Retry-After} (60 s si no viene). En todos
 * esos casos lanza {@link ProveedorNoDisponibleException} (503 PROVEEDOR_NO_DISPONIBLE). Una respuesta con una
 * forma inesperada se trata como "sin datos".
 * <p>
 * Los logs nunca incluyen el texto buscado ni la respuesta: solo la operación y el código HTTP.
 */
@Service
public class OpenFoodFactsServicio {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFoodFactsServicio.class);

    /** Campos que se piden (§3.1); el resto de la respuesta no se usa. */
    static final String CAMPOS = "code,product_name,brands,quantity,serving_size,serving_quantity,nutrition_data_per,"
            + "nutriments,ingredients_text,allergens_tags,traces_tags,categories_tags,image_front_small_url";
    static final int TAMANO_PAGINA = 20;
    static final int PAGINA_MAXIMA = 50;
    static final int LONGITUD_MINIMA_BUSQUEDA = 2;
    static final int LONGITUD_MAXIMA_BUSQUEDA = 60;
    static final Duration CAIDA_POR_DEFECTO = Duration.ofSeconds(60);
    private static final Duration ESPERA_MAXIMA = Duration.ofHours(1);
    private static final Duration ESPERA_REINTENTO = Duration.ofMillis(250);

    private static final Pattern CODIGO = Pattern.compile("\\d{4,14}");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    /** Resultado de una llamada: código HTTP, cabeceras y cuerpo (nulo si no hay o no se pudo leer). */
    private record Respuesta(int estado, HttpHeaders cabeceras, Map<String, Object> cuerpo) {
    }

    private final RestClient api;
    private final RestClient busqueda;
    private final boolean configurado;
    private final LimitadorProveedor limitador;
    private final EstadoProveedor estadoProveedor;
    private final Clock reloj;
    private final Duration esperaReintento;

    @Autowired
    public OpenFoodFactsServicio(RestClient.Builder constructor, IntegracionProperties propiedades,
            LimitadorProveedor limitador, EstadoProveedor estadoProveedor, Clock reloj) {
        this(constructor, propiedades, limitador, estadoProveedor, reloj, ESPERA_REINTENTO);
    }

    /** Para los tests: permite acortar la espera antes del reintento. */
    OpenFoodFactsServicio(RestClient.Builder constructor, IntegracionProperties propiedades,
            LimitadorProveedor limitador, EstadoProveedor estadoProveedor, Clock reloj, Duration esperaReintento) {
        IntegracionProperties.Proveedor off = propiedades.de(ProveedorExterno.OPEN_FOOD_FACTS);
        this.limitador = limitador;
        this.estadoProveedor = estadoProveedor;
        this.reloj = reloj;
        this.esperaReintento = esperaReintento;
        this.configurado = off.baseUrl() != null && off.searchUrl() != null && off.userAgent() != null;

        RestClient.Builder comun = constructor.clone()
                .requestFactory(fabricaConTimeouts(off))
                .defaultHeaders(cabeceras -> {
                    if (off.userAgent() != null) {
                        cabeceras.set(HttpHeaders.USER_AGENT, off.userAgent());
                    }
                });
        RestClient.Builder constructorApi = comun.clone();
        if (off.baseUrl() != null) {
            constructorApi.baseUrl(off.baseUrl());
        }
        if (off.usaAutenticacionStaging()) {
            constructorApi.defaultHeaders(cabeceras -> cabeceras.setBasicAuth(off.usuarioStaging(), off.claveStaging()));
        }
        RestClient.Builder constructorBusqueda = comun.clone();
        if (off.searchUrl() != null) {
            constructorBusqueda.baseUrl(off.searchUrl());
        }
        this.api = constructorApi.build();
        this.busqueda = constructorBusqueda.build();
    }

    // ---------------------------------------------------------------------------------------------
    // Operaciones
    // ---------------------------------------------------------------------------------------------

    /**
     * Producto por código de barras. La API responde 200 con {@code status: 0} si no existe (§3.2): entonces,
     * igual que con un 404, devuelve vacío.
     *
     * @throws PeticionInvalidaException      si el código no tiene entre 4 y 14 dígitos
     * @throws ProveedorNoDisponibleException si el proveedor está caído, sin cupo o falla
     */
    public Optional<ProductoExternoDto> buscarPorCodigo(String codigo) {
        String limpio = validarCodigo(codigo);
        Map<String, Object> cuerpo = ejecutar(OperacionProveedor.PRODUCTO, api, uri -> uri
                .path("/api/v2/product/{codigo}.json")
                .queryParam("fields", "{campos}")
                .build(limpio, CAMPOS));
        BigDecimal status = cuerpo == null ? null : RespuestaOff.decimal(cuerpo.get("status"));
        if (status == null || status.compareTo(BigDecimal.ONE) != 0
                || !(cuerpo.get("product") instanceof Map<?, ?> producto)) {
            return Optional.empty();
        }
        return Optional.of(RespuestaOff.aProducto(producto, limpio));
    }

    /**
     * Búsqueda por texto en Search-a-licious (la API v2 no tiene búsqueda de texto, §3.1), 20 resultados por
     * página. Solo debe llamarse cuando el usuario lo pide expresamente, nunca mientras escribe: el límite de
     * 10 búsquedas por minuto lo comparten todos los usuarios. Se guarda en caché una hora por texto normalizado
     * (sin tildes ni mayúsculas) y página; los errores no se guardan.
     *
     * @param pagina desde 1
     * @throws PeticionInvalidaException      si el texto no tiene entre 2 y 60 caracteres o la página no es válida
     * @throws ProveedorNoDisponibleException si el proveedor está caído, sin cupo o falla
     */
    @Cacheable(cacheNames = CacheConfig.OFF_BUSQUEDA,
            key = "T(com.nutrisport.integracion.openfoodfacts.OpenFoodFactsServicio).claveBusqueda(#texto, #pagina)")
    public List<ProductoExternoDto> buscarPorTexto(String texto, int pagina) {
        String consulta = validarTexto(texto);
        if (pagina < 1 || pagina > PAGINA_MAXIMA) {
            throw invalida("pagina", "La página debe estar entre 1 y " + PAGINA_MAXIMA);
        }
        Map<String, Object> cuerpo = ejecutar(OperacionProveedor.BUSQUEDA, busqueda, uri -> uri
                .path("/search")
                .queryParam("q", "{q}")
                .queryParam("page", "{pagina}")
                .queryParam("page_size", "{tamano}")
                .queryParam("fields", "{campos}")
                .build(consulta, pagina, TAMANO_PAGINA, CAMPOS));
        if (cuerpo == null || !(cuerpo.get("hits") instanceof List<?> resultados)) {
            return List.of();
        }
        return resultados.stream()
                .filter(resultado -> resultado instanceof Map<?, ?>)
                .map(resultado -> RespuestaOff.aProducto((Map<?, ?>) resultado, null))
                .filter(producto -> producto.codigo() != null)
                .toList();
    }

    /** Clave de la caché de búsquedas: texto normalizado (sin tildes ni mayúsculas) y página. */
    public static String claveBusqueda(String texto, int pagina) {
        return (texto == null ? "" : TextoNormalizado.paraBusqueda(texto)) + "|" + pagina;
    }

    // ---------------------------------------------------------------------------------------------
    // Llamadas con límites, reintento y caída
    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> ejecutar(OperacionProveedor operacion, RestClient cliente,
            Function<UriBuilder, URI> uri) {
        if (!configurado) {
            LOG.warn("Open Food Facts no está configurado (faltan OFF_BASE_URL, OFF_SEARCH_URL u OFF_USER_AGENT)");
            throw new ProveedorNoDisponibleException(CAIDA_POR_DEFECTO.toSeconds());
        }
        for (int intento = 1; ; intento++) {
            comprobarDisponible(operacion);
            Respuesta respuesta = llamar(cliente, uri, operacion);

            boolean fallo = respuesta == null || (respuesta.estado() >= 500 && respuesta.estado() != 503);
            if (fallo) {
                if (intento == 1) {
                    esperarAntesDeReintentar();
                    continue;
                }
                throw caido(operacion, respuesta == null ? "sin respuesta" : String.valueOf(respuesta.estado()),
                        CAIDA_POR_DEFECTO);
            }
            if (respuesta.estado() == 429 || respuesta.estado() == 503) {
                throw caido(operacion, String.valueOf(respuesta.estado()), retryAfter(respuesta.cabeceras()));
            }
            if (respuesta.estado() == 404) {
                return null;
            }
            if (respuesta.estado() >= 400) {
                // 400, 401 (credenciales de staging)...: error de configuración que no se arregla reintentando
                throw caido(operacion, String.valueOf(respuesta.estado()), CAIDA_POR_DEFECTO);
            }
            return respuesta.cuerpo();
        }
    }

    /** Estado del proveedor y cupo propio; si no se puede llamar, 503 con los segundos de espera. */
    private void comprobarDisponible(OperacionProveedor operacion) {
        if (!estadoProveedor.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)) {
            throw new ProveedorNoDisponibleException(
                    estadoProveedor.segundosHastaDisponible(ProveedorExterno.OPEN_FOOD_FACTS));
        }
        if (!limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, operacion)) {
            throw new ProveedorNoDisponibleException(
                    limitador.segundosHastaCupo(ProveedorExterno.OPEN_FOOD_FACTS, operacion));
        }
    }

    /** Hace la petición; nulo si no hubo respuesta (timeout, conexión rechazada...). */
    private Respuesta llamar(RestClient cliente, Function<UriBuilder, URI> uri, OperacionProveedor operacion) {
        try {
            return cliente.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> leer(respuesta, operacion), true);
        } catch (ResourceAccessException e) {
            LOG.info("Open Food Facts no responde ({}): {}", operacion, e.getClass().getSimpleName());
            return null;
        }
    }

    private static Respuesta leer(ConvertibleClientHttpResponse respuesta, OperacionProveedor operacion)
            throws IOException {
        int estado = respuesta.getStatusCode().value();
        Map<String, Object> cuerpo = null;
        if (estado >= 200 && estado < 300) {
            try {
                cuerpo = respuesta.bodyTo(JSON);
            } catch (RuntimeException e) {
                // JSON mal formado, tipo de contenido inesperado...: el conversor puede lanzar varias excepciones
                LOG.warn("Respuesta de Open Food Facts con una forma inesperada ({}): se trata como sin datos",
                        operacion);
            }
        }
        return new Respuesta(estado, respuesta.getHeaders(), cuerpo);
    }

    private ProveedorNoDisponibleException caido(OperacionProveedor operacion, String motivo, Duration espera) {
        LOG.warn("Open Food Facts no disponible ({}: {}); no se le llamará durante {} s", operacion, motivo,
                espera.toSeconds());
        estadoProveedor.marcarCaido(ProveedorExterno.OPEN_FOOD_FACTS, espera);
        return new ProveedorNoDisponibleException(espera.toSeconds());
    }

    /** {@code Retry-After} en segundos o como fecha HTTP; 60 s si falta o no se entiende; como mucho 1 hora. */
    private Duration retryAfter(HttpHeaders cabeceras) {
        String valor = cabeceras.getFirst(HttpHeaders.RETRY_AFTER);
        Duration espera = null;
        if (valor != null && !valor.isBlank()) {
            String limpio = valor.strip();
            try {
                espera = limpio.chars().allMatch(Character::isDigit)
                        ? Duration.ofSeconds(Long.parseLong(limpio))
                        : Duration.between(reloj.instant(),
                                ZonedDateTime.parse(limpio, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (NumberFormatException | DateTimeParseException | ArithmeticException e) {
                espera = null;
            }
        }
        if (espera == null || espera.isNegative() || espera.isZero()) {
            return CAIDA_POR_DEFECTO;
        }
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private void esperarAntesDeReintentar() {
        if (esperaReintento.isZero()) {
            return;
        }
        try {
            Thread.sleep(esperaReintento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProveedorNoDisponibleException(CAIDA_POR_DEFECTO.toSeconds());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private static JdkClientHttpRequestFactory fabricaConTimeouts(IntegracionProperties.Proveedor off) {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(off.timeoutConexionMs()))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofMillis(off.timeoutLecturaMs()));
        return fabrica;
    }

    private static String validarCodigo(String codigo) {
        String limpio = codigo == null ? "" : codigo.strip();
        if (!CODIGO.matcher(limpio).matches()) {
            throw invalida("codigo", "El código de barras debe tener entre 4 y 14 dígitos");
        }
        return limpio;
    }

    private static String validarTexto(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        int longitud = TextoNormalizado.paraBusqueda(limpio).length();
        if (longitud < LONGITUD_MINIMA_BUSQUEDA || longitud > LONGITUD_MAXIMA_BUSQUEDA) {
            throw invalida("q", "La búsqueda debe tener entre " + LONGITUD_MINIMA_BUSQUEDA + " y "
                    + LONGITUD_MAXIMA_BUSQUEDA + " caracteres");
        }
        return limpio;
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
