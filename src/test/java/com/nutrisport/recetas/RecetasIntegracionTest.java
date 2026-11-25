package com.nutrisport.recetas;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.servicio.ImportadorAlimentosCsv;
import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.recetas.servicio.ImportadorRecetasJson;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Catálogo de recetas (§3.D y §7.3) de punta a punta, con los datos de desarrollo reales (alimentos y recetas
 * propias) y TheMealDB simulado por un servidor HTTP local.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecetasIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso MEALDB = new ServidorHttpFalso();
    private static final UUID CATEGORIA_POLLO = UUID.fromString("c0000000-0000-0000-0001-000000000007");

    @DynamicPropertySource
    static void proveedorLocal(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.themealdb.base-url", () -> MEALDB.url() + "/api/json/v1");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private LimitadorProveedor limitador;
    @Autowired
    private EstadoProveedor estado;
    @Autowired
    private ImportadorAlimentosCsv importadorAlimentos;
    @Autowired
    private ImportadorRecetasJson importadorRecetas;

    private UsuarioConToken deportista;
    private ResultadoImportacion resultadoRecetas;

    @BeforeEach
    void preparar() throws Exception {
        MEALDB.reiniciar();
        cacheManager.getCache(CacheConfig.MEALDB_LISTAS).clear();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
        jdbc.execute("TRUNCATE receta, usuario CASCADE");
        if (jdbc.queryForObject("SELECT count(*) FROM alimento", Integer.class) == 0) {
            try (InputStream csv = new ClassPathResource("datos/alimentos_desarrollo.csv").getInputStream()) {
                importadorAlimentos.importar(csv, FuenteDato.ADMIN, "DEV", true);
            }
        }
        try (InputStream json = new ClassPathResource("datos/recetas_desarrollo.json").getInputStream()) {
            resultadoRecetas = importadorRecetas.importar(json);
        }
        deportista = crearUsuarioYToken();
    }

    @AfterAll
    void vaciar() {
        jdbc.execute("TRUNCATE receta, alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Datos de desarrollo
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasRecetasDeDesarrolloSeImportanConTodosSusIngredientesEmparejados() throws Exception {
        assertThat(resultadoRecetas.creados()).isEqualTo(20);
        assertThat(resultadoRecetas.errores()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta_ingrediente WHERE alimento_id IS NULL",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta WHERE publicada AND origen = 'PROPIA'",
                Integer.class)).isEqualTo(20);

        // Volver a importar no duplica
        try (InputStream json = new ClassPathResource("datos/recetas_desarrollo.json").getInputStream()) {
            assertThat(importadorRecetas.importar(json).creados()).isZero();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda
    // ---------------------------------------------------------------------------------------------

    @Test
    void buscaEnElCatalogoYEnTheMealDbConElTexto() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        buscar("q", "chicken")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themealdbDisponible").value(true))
                .andExpect(jsonPath("$.themealdb.length()").value(1))
                .andExpect(jsonPath("$.themealdb[0].id").doesNotExist())
                .andExpect(jsonPath("$.themealdb[0].referenciaExterna").value("52850"))
                .andExpect(jsonPath("$.themealdb[0].estadoNutricional").value("PENDIENTE"))
                .andExpect(jsonPath("$.themealdb[0].compatible").value(false))
                .andExpect(jsonPath("$.themealdb[0].fuente.proveedor").value("THEMEALDB"))
                .andExpect(jsonPath("$.themealdb[0].fuente.url").value("https://www.themealdb.com/meal/52850"))
                .andExpect(jsonPath("$.themealdb[0].fuente.atribucion").value("Receta e imagen: TheMealDB"));

        assertThat(MEALDB.recibidas().get(0).ruta()).isEqualTo("/api/json/v1/1/search.php");
        assertThat(MEALDB.recibidas().get(0).parametro("s")).isEqualTo("chicken");
    }

    @Test
    void elTextoSeBuscaSinTildesEnElCatalogo() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-vacio.json")));

        buscar("q", "salmon")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogo.content.length()").value(1))
                .andExpect(jsonPath("$.catalogo.content[0].nombre").value("Salmón al horno con patata y espárragos"))
                .andExpect(jsonPath("$.catalogo.content[0].origen").value("PROPIA"))
                // Ingredientes emparejados y en gramos exactos: cálculo COMPLETO
                .andExpect(jsonPath("$.catalogo.content[0].estadoNutricional").value("COMPLETO"))
                .andExpect(jsonPath("$.catalogo.content[0].nutricionRacion.kcal").isNumber())
                .andExpect(jsonPath("$.catalogo.content[0].fuente").doesNotExist())
                .andExpect(jsonPath("$.themealdb.length()").value(0));
    }

    @Test
    void unaRecetaYaImportadaApareceConSuIdYNoComoPendiente() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String id = JsonPath.read(importar("52850").andReturn().getResponse().getContentAsString(), "$.id");
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        buscar("q", "couscous")
                .andExpect(jsonPath("$.themealdb[0].id").value(id))
                .andExpect(jsonPath("$.themealdb[0].estadoNutricional").value("INCOMPLETO"))
                // Importada pero sin publicar: no sale en el catálogo
                .andExpect(jsonPath("$.catalogo.content.length()").value(0));
    }

    @Test
    void conOrigenPropiaOConFiltrosNutricionalesNoSeConsultaTheMealDb() throws Exception {
        buscar("q", "pollo", "origen", "propia")
                .andExpect(jsonPath("$.catalogo.content[0].nombre").value("Pollo a la plancha con arroz y brócoli"));
        buscar("franja", "DESAYUNO")
                .andExpect(jsonPath("$.catalogo.totalElements").value(4));
        buscar("q", "pollo", "maxKcal", "800").andExpect(jsonPath("$.catalogo.totalElements").value(1));

        assertThat(MEALDB.recibidas()).isEmpty();
    }

    @Test
    void losFiltrosNutricionalesSoloAdmitenRecetasConCalculo() throws Exception {
        String conFiltro = buscar("maxKcal", "600", "minProteina", "40", "size", "50")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> recetas = JsonPath.read(conFiltro, "$.catalogo.content");
        assertThat(recetas).isNotEmpty().allSatisfy(receta -> {
            Map<?, ?> nutricion = (Map<?, ?>) receta.get("nutricionRacion");
            assertThat(((Number) nutricion.get("kcal")).doubleValue()).isLessThanOrEqualTo(600);
            assertThat(((Number) nutricion.get("proteinas")).doubleValue()).isGreaterThanOrEqualTo(40);
        });

        // Una receta INCOMPLETO no pasa el filtro aunque sus valores guardados encajen
        String nombre = (String) recetas.get(0).get("nombre");
        jdbc.update("UPDATE receta SET estado_nutricional = 'INCOMPLETO' WHERE nombre = ?", nombre);
        String sinElla = buscar("maxKcal", "600", "minProteina", "40", "size", "50")
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(sinElla, "$.catalogo.content[*].nombre")).doesNotContain(nombre);
        buscar("maxKcal", "1").andExpect(jsonPath("$.catalogo.totalElements").value(0));
    }

    @Test
    void porIngredienteBuscaEnElAlimentoEmparejadoYFiltraEnTheMealDb() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")));

        buscar("ingrediente", "platano")
                .andExpect(jsonPath("$.catalogo.totalElements").value(4))
                .andExpect(jsonPath("$.themealdb.length()").value(2));

        assertThat(MEALDB.recibidas().get(0).ruta()).isEqualTo("/api/json/v1/1/filter.php");
        assertThat(MEALDB.recibidas().get(0).parametro("i")).isEqualTo("platano");
    }

    @Test
    void porCategoriaUsaLaCategoriaEquivalenteDeTheMealDb() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-filter-chicken.json")));

        buscar("categoria", CATEGORIA_POLLO.toString())
                .andExpect(jsonPath("$.catalogo.content[0].categoria").value("Pollo"))
                .andExpect(jsonPath("$.themealdb.length()").value(2));

        assertThat(MEALDB.recibidas().get(0).parametro("c")).isEqualTo("Chicken");
    }

    @Test
    void conTheMealDbCaidoSeDevuelveElCatalogoYLaCabecera() throws Exception {
        MEALDB.encolar(Respuesta.estado(500), Respuesta.estado(500));

        buscar("q", "pollo")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Proveedor-No-Disponible", "THEMEALDB"))
                .andExpect(jsonPath("$.themealdbDisponible").value(false))
                .andExpect(jsonPath("$.catalogo.content.length()").value(1));
    }

    @Test
    void parametrosNoValidosResponden400() throws Exception {
        buscar("origen", "otra").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("origen"));
        buscar("size", "51").andExpect(status().isBadRequest());
        buscar("franja", "ALMUERZO").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------
    // Importación de TheMealDB
    // ---------------------------------------------------------------------------------------------

    @Test
    void importarUnaRecetaDeTheMealDbLaGuardaUnaVezConRacionesSupuestasYSinPublicar() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        String id = JsonPath.read(importar("52850")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("THEMEALDB"))
                .andExpect(jsonPath("$.nombre").value("Chicken Couscous"))
                .andExpect(jsonPath("$.idioma").value("en"))
                .andExpect(jsonPath("$.categoria").value("Pollo"))
                .andExpect(jsonPath("$.area").value("Moroccan"))
                .andExpect(jsonPath("$.raciones").value(4))
                .andExpect(jsonPath("$.racionesSupuestas").value(true))
                .andExpect(jsonPath("$.ingredientes.length()").value(10))
                .andExpect(jsonPath("$.ingredientes[4].nombre").value("Harissa Spice"))
                .andExpect(jsonPath("$.ingredientes[4].medidaOriginal").value("2 tblsp"))
                // Sin emparejar (USDA no está disponible en este test), pero con gramos por densidad 1
                .andExpect(jsonPath("$.ingredientes[4].estadoConversion").value("ESTIMADA"))
                .andExpect(jsonPath("$.ingredientes[4].gramos").value(30.0))
                .andExpect(jsonPath("$.ingredientes[4].alimentoId").doesNotExist())
                .andExpect(jsonPath("$.instrucciones[0].orden").value(1))
                .andExpect(jsonPath("$.nutricionRacion").doesNotExist())
                .andExpect(jsonPath("$.estadoNutricional").value("INCOMPLETO"))
                .andExpect(jsonPath("$.fuente.url").value("https://www.themealdb.com/meal/52850"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(jdbc.queryForObject("SELECT publicada FROM receta WHERE id = ?::uuid", Boolean.class, id)).isFalse();
        // La segunda vez sale del catálogo, sin llamar a TheMealDB
        importar("52850").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(MEALDB.recibidas()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta WHERE origen = 'THEMEALDB'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unaRecetaQueTheMealDbNoTieneEs404YConTheMealDbCaidoEs503() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-vacio.json")));
        importar("1").andExpect(status().isNotFound());

        MEALDB.encolar(Respuesta.estado(503));
        importar("52850").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"));
    }

    // ---------------------------------------------------------------------------------------------
    // Detalle, compatibilidad, relacionadas y favoritas
    // ---------------------------------------------------------------------------------------------

    @Test
    void elDetalleEscalaLasCantidadesALasRacionesPedidas() throws Exception {
        String id = idDe("Ensalada de quinoa, garbanzos y verduras");

        detalle(id, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.raciones").value(2))
                .andExpect(jsonPath("$.racionesSupuestas").value(false))
                .andExpect(jsonPath("$.ingredientes[0].nombre").value("Quinoa cocida"))
                .andExpect(jsonPath("$.ingredientes[0].gramos").value(200.0))
                .andExpect(jsonPath("$.ingredientes[0].estadoConversion").value("EXACTA"))
                .andExpect(jsonPath("$.franjasAptas").value(hasItem("COMIDA")));
        detalle(id, 4).andExpect(jsonPath("$.raciones").value(4)).andExpect(jsonPath("$.ingredientes[0].gramos").value(400.0));
        detalle(id, 0).andExpect(status().isBadRequest());
        detalle(UUID.randomUUID().toString(), null).andExpect(status().isNotFound());
    }

    @Test
    void compatibleSegunLasRestriccionesEstrictasDelUsuario() throws Exception {
        String yogur = idDe("Yogur griego con frutos rojos y granola");
        String hummus = idDe("Hummus con crudités");
        detalle(yogur, null).andExpect(jsonPath("$.compatible").value(true));

        preferencias("""
                [{"tipo": "INTOLERANCIA", "intolerancia": "LACTOSA", "severidad": "ESTRICTA"}]""");
        detalle(yogur, null).andExpect(jsonPath("$.compatible").value(false));
        detalle(hummus, null).andExpect(jsonPath("$.compatible").value(true));

        // Con una alergia estricta: alérgenos verificados (todos los ingredientes emparejados y revisados) y
        // ninguno de los del usuario
        String gachas = idDe("Gachas de avena con plátano y crema de cacahuete");
        preferencias("""
                [{"tipo": "ALERGIA", "alergeno": "CACAHUETES", "severidad": "ESTRICTA"}]""");
        detalle(hummus, null).andExpect(jsonPath("$.alergenosVerificados").value(true))
                .andExpect(jsonPath("$.compatible").value(true));
        detalle(gachas, null).andExpect(jsonPath("$.alergenos").value(hasItem("CACAHUETES")))
                .andExpect(jsonPath("$.compatible").value(false));
        preferencias("""
                [{"tipo": "ALERGIA", "alergeno": "SESAMO", "severidad": "ESTRICTA"}]""");
        detalle(hummus, null).andExpect(jsonPath("$.compatible").value(false));
    }

    @Test
    void unaRecetaPrivadaDeOtroUsuarioResponde404YSuPropietarioSiLaVe() throws Exception {
        UsuarioConToken otro = crearUsuarioYToken();
        String privada = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO receta (id, origen, nombre, nombre_normalizado, visibilidad, propietario_id, creado_en,
                                    actualizado_en)
                VALUES (?::uuid, 'PROPIA', 'Receta secreta', 'receta secreta', 'PRIVADO', ?, now(), now())""",
                privada, otro.usuario().getId());

        detalle(privada, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(conToken(get("/api/v1/recetas/" + privada + "/relacionadas"))).andExpect(status().isNotFound());
        mockMvc.perform(conToken(put("/api/v1/recetas/" + privada + "/favorita"))).andExpect(status().isNotFound());
        buscar("q", "secreta", "origen", "propia").andExpect(jsonPath("$.catalogo.totalElements").value(0));

        mockMvc.perform(get("/api/v1/recetas/" + privada).header("Authorization", otro.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Receta secreta"));
    }

    @Test
    void relacionadasDeLaMismaCategoriaSinLaPropiaReceta() throws Exception {
        String gachas = idDe("Gachas de avena con plátano y crema de cacahuete");

        mockMvc.perform(conToken(get("/api/v1/recetas/" + gachas + "/relacionadas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].categoria").value(everyItem(org.hamcrest.Matchers.is("Desayunos"))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(gachas))));
    }

    @Test
    void marcarYQuitarFavoritaEsIdempotente() throws Exception {
        String id = idDe("Skyr con nueces y miel");

        mockMvc.perform(conToken(put("/api/v1/recetas/" + id + "/favorita"))).andExpect(status().isNoContent());
        mockMvc.perform(conToken(put("/api/v1/recetas/" + id + "/favorita"))).andExpect(status().isNoContent());
        mockMvc.perform(conToken(get("/api/v1/recetas/favoritas")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].favorita").value(true));
        detalle(id, null).andExpect(jsonPath("$.favorita").value(true));
        buscar("q", "skyr", "origen", "propia").andExpect(jsonPath("$.catalogo.content[0].favorita").value(true));

        mockMvc.perform(conToken(delete("/api/v1/recetas/" + id + "/favorita"))).andExpect(status().isNoContent());
        mockMvc.perform(conToken(delete("/api/v1/recetas/" + id + "/favorita"))).andExpect(status().isNoContent());
        mockMvc.perform(conToken(get("/api/v1/recetas/favoritas"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(put("/api/v1/recetas/" + UUID.randomUUID() + "/favorita")))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriasYPermisosDelAdministrador() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/recetas/categorias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(19))
                .andExpect(jsonPath("$[?(@.nombre == 'Pollo')].codigoExterno").value(hasItem("Chicken")));

        UsuarioConToken admin = crearAdminYToken();
        String id = idDe("Skyr con nueces y miel");
        mockMvc.perform(get("/api/v1/recetas/" + id).header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/recetas/" + id + "/favorita").header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------

    private ResultActions buscar(String... parametros) throws Exception {
        MockHttpServletRequestBuilder peticion = conToken(get("/api/v1/recetas"));
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions importar(String idMeal) throws Exception {
        return mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/" + idMeal)));
    }

    private ResultActions detalle(String id, Integer raciones) throws Exception {
        MockHttpServletRequestBuilder peticion = conToken(get("/api/v1/recetas/" + id));
        if (raciones != null) {
            peticion.param("raciones", raciones.toString());
        }
        return mockMvc.perform(peticion);
    }

    private void preferencias(String lista) throws Exception {
        mockMvc.perform(conToken(put("/api/v1/perfil/preferencias")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferencias\": " + lista + "}"))
                .andExpect(status().isOk());
    }

    private String idDe(String nombre) {
        List<String> ids = jdbc.queryForList("SELECT id::text FROM receta WHERE nombre = ?", String.class, nombre);
        assertThat(ids).as(nombre).hasSize(1);
        return ids.get(0);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", deportista.cabeceraAutorizacion());
    }
}
