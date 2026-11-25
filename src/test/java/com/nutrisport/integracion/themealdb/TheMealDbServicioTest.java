package com.nutrisport.integracion.themealdb;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Peticion;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Cliente de TheMealDB (docs/API_INTEGRATIONS.md §4) contra un servidor HTTP local con las respuestas grabadas
 * de {@code src/test/resources/integracion}. Nunca llama a la API real.
 */
class TheMealDbServicioTest {

    private static final String RUTA_BASE = "/api/json/v1";
    private static final String CLAVE = "1";

    private final ServidorHttpFalso servidor = new ServidorHttpFalso();
    private final EstadoProveedor estado = new EstadoProveedor(Clock.systemUTC());
    private final LimitadorProveedor limitador = new LimitadorProveedor();
    private final TheMealDbServicio servicio = servicio(CLAVE);

    @AfterEach
    void cerrar() {
        servidor.close();
    }

    // ---------------------------------------------------------------------------------------------
    // Recetas completas
    // ---------------------------------------------------------------------------------------------

    @Test
    void detallePorIdConLaClaveEnLaRutaYLosIngredientesDeLasParejasNoVacias() {
        servidor.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        RecetaExternaDto receta = servicio.porId("52850").orElseThrow();

        Peticion peticion = servidor.recibidas().get(0);
        assertThat(peticion.ruta()).isEqualTo(RUTA_BASE + "/1/lookup.php");
        assertThat(peticion.parametro("i")).isEqualTo("52850");
        // TheMealDB no usa autenticación: la clave va en la ruta
        assertThat(peticion.cabecera("Authorization")).isNull();

        assertThat(receta.idMeal()).isEqualTo("52850");
        assertThat(receta.nombre()).isEqualTo("Chicken Couscous");
        assertThat(receta.categoria()).isEqualTo("Chicken");
        assertThat(receta.area()).isEqualTo("Moroccan");
        assertThat(receta.instrucciones()).startsWith("Heat the olive oil");
        assertThat(receta.imagenUrl()).isEqualTo("https://www.themealdb.com/images/media/meals/qxytrx1511304021.jpg");
        assertThat(receta.etiquetas()).isEmpty();
        assertThat(receta.youtube()).isNull();
        // 10 parejas con ingrediente; la 11 ("" y "") y las 12–20 (null) se ignoran
        assertThat(receta.ingredientes()).hasSize(10);
        assertThat(receta.ingredientes().get(0)).isEqualTo(new IngredienteExternoDto("Olive Oil", "1 tbsp"));
        assertThat(receta.ingredientes().get(2)).isEqualTo(new IngredienteExternoDto("Chicken Breast", "200g"));
        // "2 tblsp " llega con un espacio al final
        assertThat(receta.ingredientes().get(4)).isEqualTo(new IngredienteExternoDto("Harissa Spice", "2 tblsp"));
        assertThat(receta.ingredientes().get(9)).isEqualTo(new IngredienteExternoDto("Coriander", "Handful"));
    }

    @Test
    void etiquetasYoutubeYHuecosRaros() {
        servidor.encolar(Respuesta.json(recurso("integracion/themealdb-receta-ficticia.json")));

        RecetaExternaDto receta = servicio.porId("99999").orElseThrow();

        assertThat(receta.etiquetas()).containsExactly("Meat", "Casserole");
        assertThat(receta.youtube()).isEqualTo("https://www.youtube.com/watch?v=ficticio");
        // Se recortan los espacios; una medida vacía o nula queda nula; un ingrediente vacío se ignora aunque
        // tenga medida
        assertThat(receta.ingredientes()).containsExactly(
                new IngredienteExternoDto("Rice", "2 cups"),
                new IngredienteExternoDto("Salt", null),
                new IngredienteExternoDto("Peas", null));
    }

    @Test
    void sinResultadosTheMealDbRespondeMealsNullYEsUnaListaVacia() {
        servidor.encolar(Respuesta.json(recurso("integracion/mealdb-vacio.json")),
                Respuesta.json(recurso("integracion/mealdb-vacio.json")),
                Respuesta.json(recurso("integracion/mealdb-vacio.json")));

        assertThat(servicio.porId("1")).isEmpty();
        assertThat(servicio.buscarPorNombre("zzzz")).isEmpty();
        assertThat(servicio.filtrarPorIngrediente("unicornio")).isEmpty();
        assertThat(estado.estaDisponible(ProveedorExterno.THEMEALDB)).isTrue();
    }

    @Test
    void buscarPorNombreDevuelveRecetasCompletas() {
        servidor.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        List<RecetaExternaDto> recetas = servicio.buscarPorNombre(" couscous ");

        assertThat(servidor.recibidas().get(0).ruta()).isEqualTo(RUTA_BASE + "/1/search.php");
        assertThat(servidor.recibidas().get(0).parametro("s")).isEqualTo("couscous");
        assertThat(recetas).singleElement().satisfies(receta -> assertThat(receta.ingredientes()).hasSize(10));
    }

    // ---------------------------------------------------------------------------------------------
    // Filtros y listas
    // ---------------------------------------------------------------------------------------------

    @Test
    void losFiltrosSoloDevuelvenIdNombreEImagen() {
        servidor.encolar(Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")),
                Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")),
                Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")));

        List<RecetaResumenExternaDto> porIngrediente = servicio.filtrarPorIngrediente("chicken_breast");
        servicio.filtrarPorCategoria("Chicken");
        servicio.filtrarPorArea("Moroccan");

        assertThat(servidor.recibidas()).extracting(Peticion::ruta).containsOnly(RUTA_BASE + "/1/filter.php");
        assertThat(servidor.recibidas().get(0).parametro("i")).isEqualTo("chicken_breast");
        assertThat(servidor.recibidas().get(1).parametro("c")).isEqualTo("Chicken");
        assertThat(servidor.recibidas().get(2).parametro("a")).isEqualTo("Moroccan");
        // El resultado sin idMeal se descarta
        assertThat(porIngrediente).containsExactly(
                new RecetaResumenExternaDto("52850", "Chicken Couscous",
                        "https://www.themealdb.com/images/media/meals/qxytrx1511304021.jpg"),
                new RecetaResumenExternaDto("52772", "Teriyaki Chicken Casserole",
                        "https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg"));
    }

    @Test
    void categoriasYAreas() {
        servidor.encolar(Respuesta.json(recurso("integracion/themealdb-categorias.json")),
                Respuesta.json(recurso("integracion/themealdb-areas.json")));

        List<CategoriaExternaDto> categorias = servicio.categorias();
        List<String> areas = servicio.areas();

        assertThat(servidor.recibidas().get(0).ruta()).isEqualTo(RUTA_BASE + "/1/categories.php");
        assertThat(servidor.recibidas().get(1).ruta()).isEqualTo(RUTA_BASE + "/1/list.php");
        assertThat(servidor.recibidas().get(1).parametro("a")).isEqualTo("list");
        assertThat(categorias).extracting(CategoriaExternaDto::nombre).containsExactly("Beef", "Chicken");
        assertThat(categorias.get(1).id()).isEqualTo("2");
        assertThat(categorias.get(1).imagenUrl()).isEqualTo("https://www.themealdb.com/images/category/chicken.png");
        assertThat(areas).containsExactly("American", "British", "Moroccan");
    }

    // ---------------------------------------------------------------------------------------------
    // Validación, errores y límites (comunes con Open Food Facts)
    // ---------------------------------------------------------------------------------------------

    @Test
    void entradasNoValidasSeRechazanSinLlamar() {
        assertThatThrownBy(() -> servicio.porId("52850&i=1")).isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscarPorNombre("a")).isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.filtrarPorArea(" ")).isInstanceOf(PeticionInvalidaException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    @Test
    void dosErroresSeguidosMarcanTheMealDbComoCaidoSinAfectarAOtrosProveedores() {
        servidor.encolar(Respuesta.estado(500), Respuesta.estado(502));

        assertThatThrownBy(() -> servicio.porId("52850"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(60));
        assertThat(servidor.recibidas()).hasSize(2);
        assertThat(estado.estaDisponible(ProveedorExterno.THEMEALDB)).isFalse();
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isTrue();
    }

    @Test
    void un429RespetaRetryAfterSinReintentar() {
        servidor.encolar(Respuesta.estado(429, Map.of("Retry-After", "30")));

        assertThatThrownBy(() -> servicio.buscarPorNombre("couscous"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(30));
        assertThat(servidor.recibidas()).hasSize(1);
    }

    @Test
    void sinCupoNoSeLlamaATheMealDb() {
        // TheMealDB: 5 de ráfaga compartidos por todas las operaciones
        for (int i = 0; i < 5; i++) {
            servidor.encolar(Respuesta.json(recurso("integracion/mealdb-vacio.json")));
            servicio.porId("52850");
        }

        assertThatThrownBy(() -> servicio.categorias()).isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).hasSize(5);
    }

    @Test
    void sinClaveConfiguradaNoSeLlama() {
        TheMealDbServicio sinClave = servicio(null);

        assertThatThrownBy(() -> sinClave.porId("52850")).isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    private TheMealDbServicio servicio(String clave) {
        IntegracionProperties propiedades = new IntegracionProperties(null,
                new IntegracionProperties.Proveedor(servidor.url() + RUTA_BASE + "/", null, null, null, null, clave,
                        1000, 300),
                null);
        return new TheMealDbServicio(RestClient.builder(), propiedades, limitador, estado, Clock.systemUTC(),
                Duration.ZERO);
    }
}
