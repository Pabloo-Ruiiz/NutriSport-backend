package com.nutrisport.integracion.themealdb;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * TheMealDB con el contexto de Spring: cachés de filtros, listas y categorías ({@code @Cacheable}, que solo actúa
 * a través del proxy) y coherencia de {@link TablaCategoriasMealDb} con las categorías de la base de datos.
 */
class TheMealDbIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso SERVIDOR = new ServidorHttpFalso();

    @DynamicPropertySource
    static void proveedorLocal(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.themealdb.base-url", () -> SERVIDOR.url() + "/api/json/v1");
    }

    private final TheMealDbServicio servicio;
    private final CacheManager cacheManager;
    private final LimitadorProveedor limitador;
    private final EstadoProveedor estado;
    private final JdbcTemplate jdbc;

    @Autowired
    TheMealDbIntegracionTest(TheMealDbServicio servicio, CacheManager cacheManager, LimitadorProveedor limitador,
            EstadoProveedor estado, JdbcTemplate jdbc) {
        this.servicio = servicio;
        this.cacheManager = cacheManager;
        this.limitador = limitador;
        this.estado = estado;
        this.jdbc = jdbc;
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        SERVIDOR.reiniciar();
        cacheManager.getCache(CacheConfig.MEALDB_LISTAS).clear();
        cacheManager.getCache(CacheConfig.MEALDB_CATEGORIAS).clear();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
    }

    @Test
    void losFiltrosSeGuardanEnCachePorTipoYValorSinMezclarse() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")),
                Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")));

        servicio.filtrarPorIngrediente("Chicken");
        // Mismo valor sin distinguir mayúsculas: sale de la caché
        servicio.filtrarPorIngrediente("chicken ");
        assertThat(SERVIDOR.recibidas()).hasSize(1);

        // El mismo valor en otro filtro es otra consulta
        servicio.filtrarPorCategoria("Chicken");
        assertThat(SERVIDOR.recibidas()).hasSize(2);
        assertThat(SERVIDOR.recibidas().get(1).parametro("c")).isEqualTo("Chicken");
    }

    @Test
    void categoriasYAreasSeGuardanEnCache() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/themealdb-categorias.json")),
                Respuesta.json(recurso("integracion/themealdb-areas.json")));

        servicio.categorias();
        servicio.categorias();
        servicio.areas();
        servicio.areas();

        assertThat(SERVIDOR.recibidas()).hasSize(2);
    }

    @Test
    void elDetalleYLaBusquedaPorNombreNoSeGuardanEnCache() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")),
                Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        servicio.porId("52850");
        servicio.porId("52850");

        assertThat(SERVIDOR.recibidas()).hasSize(2);
    }

    @Test
    void laTablaDeCategoriasCoincideConLasCategoriasDeLaBaseDeDatos() {
        Map<String, UUID> enBaseDeDatos = jdbc.queryForList(
                        "SELECT codigo_externo, id FROM categoria WHERE codigo_externo IS NOT NULL").stream()
                .collect(Collectors.toMap(fila -> (String) fila.get("codigo_externo"), fila -> (UUID) fila.get("id")));

        assertThat(TablaCategoriasMealDb.tabla()).isEqualTo(enBaseDeDatos);
        assertThat(TablaCategoriasMealDb.categoriaUOtras("Desconocida")).isEqualTo(TablaCategoriasMealDb.OTRAS);
        assertThat(TablaCategoriasMealDb.categoriaPara("chicken")).isEqualTo(TablaCategoriasMealDb.categoriaPara("Chicken"));
        assertThat(TablaCategoriasMealDb.codigoPara(enBaseDeDatos.get("Seafood"))).contains("Seafood");
    }
}
