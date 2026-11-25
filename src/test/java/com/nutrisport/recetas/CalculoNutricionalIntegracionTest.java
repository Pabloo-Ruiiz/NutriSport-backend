package com.nutrisport.recetas;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.MapeoIngrediente;
import com.nutrisport.catalogo.entidad.MetodoMapeo;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.catalogo.repositorio.MapeoIngredienteRepositorio;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Cálculo nutricional de recetas (docs/API_INTEGRATIONS.md §7) y su administración (§7.10), con el ejemplo de
 * "Chicken Couscous" de §7.2: la respuesta real de TheMealDB y emparejamientos ya guardados, para que el cálculo no
 * tenga que llamar a USDA (el test lo comprueba).
 */
@AutoConfigureMockMvc
class CalculoNutricionalIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso MEALDB = new ServidorHttpFalso();
    private static final ServidorHttpFalso USDA = new ServidorHttpFalso();
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");
    private static final UUID CATEGORIA_POLLO = UUID.fromString("c0000000-0000-0000-0001-000000000007");

    @DynamicPropertySource
    static void proveedoresLocales(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.themealdb.base-url", () -> MEALDB.url() + "/api/json/v1");
        registro.add("app.integracion.usda.base-url", () -> USDA.url() + "/fdc/v1");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private MapeoIngredienteRepositorio mapeoRepositorio;
    @Autowired
    private LimitadorProveedor limitador;
    @Autowired
    private EstadoProveedor estado;

    private UsuarioConToken deportista;
    private UsuarioConToken admin;
    private Alimento cuscus;
    private Alimento pechuga;

    @BeforeEach
    void preparar() {
        limpiar();
        deportista = crearUsuarioYToken();
        admin = crearAdminYToken();
        // Valores sencillos por 100 g para poder comprobar las cuentas
        alimento("Olive oil", FuenteDato.USDA, "884", "0", "0", "100", List.of(new PorcionCasera("tbsp", new BigDecimal("13.5"))), List.of());
        alimento("Onions, raw", FuenteDato.USDA, "40", "1.1", "9.3", "0.1", List.of(new PorcionCasera("medium", new BigDecimal("110"))), List.of());
        pechuga = alimento("Pechuga de pollo cruda", FuenteDato.ADMIN, "120", "22.5", "0", "2.6", List.of(), List.of());
        alimento("Orejones", FuenteDato.ADMIN, "241", "3.4", "62.6", "0.5", List.of(new PorcionCasera("unidad", new BigDecimal("8"))), List.of());
        alimento("Garbanzos cocidos", FuenteDato.ADMIN, "164", "8.9", "27.4", "2.6", List.of(), List.of());
        cuscus = alimento("Cuscús", FuenteDato.ADMIN, "376", "12.8", "77.4", "0.6", List.of(), List.of("GLUTEN"));
        alimento("Caldo de pollo", FuenteDato.ADMIN, "7", "0.5", "0.6", "0.2", List.of(), List.of());
        mapeo("olive oil", "Olive oil");
        mapeo("onion", "Onions, raw");
        mapeo("chicken breast", "Pechuga de pollo cruda");
        mapeo("dried apricots", "Orejones");
        mapeo("chickpeas", "Garbanzos cocidos");
        mapeo("couscous", "Cuscús");
        mapeo("chicken stock", "Caldo de pollo");
        // Pendientes de revisión: no se usan y no se vuelve a preguntar a USDA
        for (String pendiente : List.of("ginger", "harissa spice", "coriander")) {
            mapeo(pendiente, null);
        }
    }

    @AfterEach
    void limpiar() {
        MEALDB.reiniciar();
        USDA.reiniciar();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
        jdbc.execute("TRUNCATE receta, mapeo_ingrediente, alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Reglas del cálculo (§7.1) con "Chicken Couscous" (§7.2)
    // ---------------------------------------------------------------------------------------------

    @Test
    void importarChickenCouscousCalculaComoEnElEjemploDeLaGuia() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));

        String id = idDe(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"), deportista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoNutricional").value("APROXIMADO"))
                .andExpect(jsonPath("$.raciones").value(4))
                .andExpect(jsonPath("$.racionesSupuestas").value(true))
                .andExpect(jsonPath("$.alergenos[0]").value("GLUTEN"))
                // Harissa, jengibre y cilantro sin emparejar
                .andExpect(jsonPath("$.alergenosVerificados").value(false)));

        String nutricion = cuerpo(mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion"), deportista))
                .andExpect(status().isOk()));
        List<Map<String, Object>> ingredientes = JsonPath.read(nutricion, "$.ingredientes");
        // masa → directa; volumen → porción USDA (exacta) o densidad 1 (estimada); unidades → "unidad"/"medium"
        comprobar(ingredientes.get(0), "Olive Oil", "13.5", "EXACTA", "USDA");
        comprobar(ingredientes.get(1), "Onion", "110.0", "ESTIMADA", "USDA");
        comprobar(ingredientes.get(2), "Chicken Breast", "200.0", "EXACTA", "ADMIN");
        comprobar(ingredientes.get(3), "Ginger", "0.0", "DESPRECIABLE", null);
        comprobar(ingredientes.get(4), "Harissa Spice", "30.0", "ESTIMADA", null);
        comprobar(ingredientes.get(5), "Dried Apricots", "80.0", "ESTIMADA", "ADMIN");
        comprobar(ingredientes.get(6), "Chickpeas", "220.0", "EXACTA", "ADMIN");
        comprobar(ingredientes.get(7), "Couscous", "200.0", "EXACTA", "ADMIN");
        comprobar(ingredientes.get(8), "Chicken Stock", "200.0", "ESTIMADA", "ADMIN");
        // "Handful" de una hierba
        comprobar(ingredientes.get(9), "Coriander", "0.0", "DESPRECIABLE", null);

        // 1023,5 g resueltos de 1053,5 g conocidos (la harissa no está emparejada) → 97 %
        assertThat((Integer) JsonPath.read(nutricion, "$.completitudPct")).isEqualTo(97);
        assertThat((String) JsonPath.read(nutricion, "$.estado")).isEqualTo("APROXIMADO");
        // 1722,9 kcal entre 4 raciones supuestas
        assertThat(numero(nutricion, "$.porRacion.kcal")).isCloseTo(430.7, within(0.2));
        assertThat(numero(nutricion, "$.porRacion.proteina")).isCloseTo(23.8, within(0.2));
        assertThat(USDA.recibidas()).isEmpty();
    }

    @Test
    void elDesgloseEscalaLosGramosYMantieneLosNutrientesPorRacion() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String id = idDe(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"), deportista)));

        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion").param("raciones", "8"), deportista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raciones").value(8))
                .andExpect(jsonPath("$.racionesSupuestas").value(true))
                .andExpect(jsonPath("$.ingredientes[2].gramos").value(400.0))
                .andExpect(jsonPath("$.porRacion.kcal").value(430.7));
        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion").param("raciones", "0"), deportista))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinMasaConocidaDeAlgunIngredienteEsIncompletoYSinNutrientes() throws Exception {
        String receta = recetaAdmin("Ensalada incompleta", 1, """
                [{"alimentoId": "%s", "gramos": 150}, {"alimentoId": "%s", "medida": "some"}]"""
                .formatted(pechuga.getId(), cuscus.getId()));
        String id = idDe(mockMvc.perform(conToken(post("/api/v1/admin/recetas"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(receta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoNutricional").value("INCOMPLETO"))
                .andExpect(jsonPath("$.nutricionRacion").doesNotExist()));

        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion"), admin))
                .andExpect(jsonPath("$.porRacion").doesNotExist())
                // No se conoce la masa del cuscús: no se puede afirmar el 90 %
                .andExpect(jsonPath("$.completitudPct").value(89))
                .andExpect(jsonPath("$.ingredientes[1].estadoConversion").value("SIN_DATOS"));
        assertThat(jdbc.queryForObject("SELECT kcal_racion FROM receta WHERE id = ?::uuid", BigDecimal.class, id)).isNull();
    }

    @Test
    void quitarDelMapeoDosIngredientesGrandesDejaLaRecetaIncompletaYSinValoresPorRacion() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String id = idDe(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"), deportista)));

        // Sin equivalente para los garbanzos (220 g) y el cuscús (200 g): se recalcula la receta
        for (String ingrediente : List.of("chickpeas", "couscous")) {
            UUID mapeoId = mapeoRepositorio.findByNombreNormalizado(ingrediente).orElseThrow().getId();
            mockMvc.perform(conToken(put("/api/v1/admin/mapeos-ingredientes/" + mapeoId), admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"alimentoId\": null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recetasRecalculadas").value(1));
        }

        // 603,5 g resueltos de 1053,5 g → 57 %
        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion"), deportista))
                .andExpect(jsonPath("$.estado").value("INCOMPLETO"))
                .andExpect(jsonPath("$.completitudPct").value(57))
                .andExpect(jsonPath("$.porRacion").doesNotExist())
                .andExpect(jsonPath("$.ingredientes[6].estadoConversion").value("EXACTA"))
                .andExpect(jsonPath("$.ingredientes[6].fuente").doesNotExist());
        mockMvc.perform(conToken(get("/api/v1/recetas/" + id), deportista))
                .andExpect(jsonPath("$.nutricionRacion").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT kcal_racion FROM receta WHERE id = ?::uuid", BigDecimal.class, id))
                .isNull();
    }

    @Test
    void fijarDosRacionesDuplicaLosValoresPorRacionYDejanDeSerSupuestas() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String importada = cuerpo(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"),
                deportista)));
        String id = JsonPath.read(importada, "$.id");
        double kcalCuatro = numero(importada, "$.nutricionRacion.kcal");
        double proteinaCuatro = numero(importada, "$.nutricionRacion.proteinas");

        String conDos = cuerpo(mockMvc.perform(conToken(patch("/api/v1/admin/recetas/" + id + "/publicacion"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"raciones\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raciones").value(2))
                .andExpect(jsonPath("$.racionesSupuestas").value(false)));

        // De 4 a 2 raciones: el doble por ración (con el redondeo a un decimal)
        assertThat(numero(conDos, "$.nutricionRacion.kcal")).isCloseTo(kcalCuatro * 2, within(0.2));
        assertThat(numero(conDos, "$.nutricionRacion.proteinas")).isCloseTo(proteinaCuatro * 2, within(0.2));
        // Las cantidades de los ingredientes son las de la receta: no cambian
        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion"), deportista))
                .andExpect(jsonPath("$.ingredientes[2].gramos").value(200.0))
                .andExpect(jsonPath("$.racionesSupuestas").value(false));
    }

    // ---------------------------------------------------------------------------------------------
    // Administración de emparejamientos
    // ---------------------------------------------------------------------------------------------

    @Test
    void fijarElEmparejamientoDeLaHarissaRecalculaLaReceta() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String id = idDe(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"), deportista)));
        Alimento harissa = alimento("Harissa", FuenteDato.ADMIN, "300", "10", "40", "12", List.of(), List.of());

        mockMvc.perform(conToken(get("/api/v1/admin/mapeos-ingredientes").param("revisado", "false"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nombreNormalizado == 'harissa spice')]").exists());
        UUID mapeoHarissa = mapeoRepositorio.findByNombreNormalizado("harissa spice").orElseThrow().getId();

        mockMvc.perform(conToken(put("/api/v1/admin/mapeos-ingredientes/" + mapeoHarissa), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alimentoId\": \"%s\"}".formatted(harissa.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("MANUAL"))
                .andExpect(jsonPath("$.revisado").value(true))
                .andExpect(jsonPath("$.alimentoNombre").value("Harissa"))
                .andExpect(jsonPath("$.recetasRecalculadas").value(1));

        mockMvc.perform(conToken(get("/api/v1/recetas/" + id + "/nutricion"), deportista))
                .andExpect(jsonPath("$.ingredientes[4].nombre").value("Harissa"))
                .andExpect(jsonPath("$.ingredientes[4].estadoConversion").value("ESTIMADA"))
                // Toda la masa resuelta, pero con estimaciones: sigue siendo APROXIMADO
                .andExpect(jsonPath("$.completitudPct").value(100))
                .andExpect(jsonPath("$.estado").value("APROXIMADO"));
        assertThat(auditorias("EMPAREJAR_INGREDIENTE")).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Administración de recetas
    // ---------------------------------------------------------------------------------------------

    @Test
    void unaRecetaPropiaConGramosExactosEsCompletaYSePublicaConSusRaciones() throws Exception {
        String receta = recetaAdmin("Pollo con cuscús", 4, """
                [{"alimentoId": "%s", "gramos": 400}, {"alimentoId": "%s", "gramos": 200, "medida": "1 taza"}]"""
                .formatted(pechuga.getId(), cuscus.getId()));
        String id = idDe(mockMvc.perform(conToken(post("/api/v1/admin/recetas"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(receta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("PROPIA"))
                .andExpect(jsonPath("$.estadoNutricional").value("COMPLETO"))
                .andExpect(jsonPath("$.completitudPct").value(100))
                // (120 × 4 + 376 × 2) / 4 raciones = 308 kcal
                .andExpect(jsonPath("$.nutricionRacion.kcal").value(308.0))
                .andExpect(jsonPath("$.alergenos[0]").value("GLUTEN"))
                .andExpect(jsonPath("$.alergenosVerificados").value(true)));
        assertThat(jdbc.queryForObject("SELECT publicada FROM receta WHERE id = ?::uuid", Boolean.class, id)).isFalse();

        mockMvc.perform(conToken(patch("/api/v1/admin/recetas/" + id + "/publicacion"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicada\": true, \"raciones\": 2, \"franjasAptas\": [\"COMIDA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raciones").value(2))
                .andExpect(jsonPath("$.racionesSupuestas").value(false))
                .andExpect(jsonPath("$.franjasAptas[0]").value("COMIDA"))
                .andExpect(jsonPath("$.nutricionRacion.kcal").value(616.0));
        assertThat(jdbc.queryForObject("SELECT publicada FROM receta WHERE id = ?::uuid", Boolean.class, id)).isTrue();

        mockMvc.perform(conToken(put("/api/v1/admin/recetas/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(recetaAdmin("Pollo solo", 1, "[{\"alimentoId\": \"%s\", \"gramos\": 100}]"
                                .formatted(pechuga.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pollo solo"))
                .andExpect(jsonPath("$.ingredientes.length()").value(1))
                .andExpect(jsonPath("$.nutricionRacion.kcal").value(120.0))
                .andExpect(jsonPath("$.alergenos.length()").value(0));

        mockMvc.perform(conToken(delete("/api/v1/admin/recetas/" + id), admin)).andExpect(status().isNoContent());
        mockMvc.perform(conToken(get("/api/v1/recetas/" + id), admin)).andExpect(status().isNotFound());
        assertThat(auditorias("CREAR_RECETA") + auditorias("PUBLICAR_RECETA") + auditorias("EDITAR_RECETA")
                + auditorias("ELIMINAR_RECETA")).isEqualTo(4);
    }

    @Test
    void unaRecetaDeTheMealDbSoloSePublicaYUnDeportistaNoPuedeAdministrar() throws Exception {
        MEALDB.encolar(Respuesta.json(recurso("integracion/mealdb-lookup-52850.json")));
        String id = idDe(mockMvc.perform(conToken(get("/api/v1/recetas/externas/themealdb/52850"), deportista)));
        String receta = recetaAdmin("Otra", 1, "[{\"alimentoId\": \"%s\", \"gramos\": 100}]".formatted(pechuga.getId()));

        mockMvc.perform(conToken(put("/api/v1/admin/recetas/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                .content(receta)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(patch("/api/v1/admin/recetas/" + id + "/publicacion"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"publicada\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.racionesSupuestas").value(true));

        mockMvc.perform(conToken(post("/api/v1/admin/recetas"), deportista).contentType(MediaType.APPLICATION_JSON)
                .content(receta)).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/api/v1/admin/mapeos-ingredientes"), deportista)).andExpect(status().isForbidden());
    }

    @Test
    void validacionesDeLasRecetasDeAdministracion() throws Exception {
        // Categoría de alimentos y alimento inexistente
        String receta = """
                {"nombre": "Mala", "categoriaId": "%s", "raciones": 2,
                 "ingredientes": [{"alimentoId": "%s", "gramos": 100}, {"alimentoId": "%s"}]}"""
                .formatted(CATEGORIA_OTROS, UUID.randomUUID(), pechuga.getId());

        mockMvc.perform(conToken(post("/api/v1/admin/recetas"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(receta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("ingredientes[1].cantidadIndicada"));
        receta = receta.replace(", {\"alimentoId\": \"" + pechuga.getId() + "\"}", "");
        mockMvc.perform(conToken(post("/api/v1/admin/recetas"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(receta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].campo").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "categoriaId", "ingredientes[0].alimentoId")));
    }

    // ---------------------------------------------------------------------------------------------

    private static void comprobar(Map<String, Object> ingrediente, String nombreOriginal, String gramos, String estado,
            String fuente) {
        assertThat(ingrediente.get("nombreOriginal")).isEqualTo(nombreOriginal);
        assertThat(new BigDecimal(String.valueOf(ingrediente.get("gramos")))).as(nombreOriginal)
                .isEqualByComparingTo(gramos);
        assertThat(ingrediente.get("estadoConversion")).as(nombreOriginal).isEqualTo(estado);
        assertThat(ingrediente.get("fuente")).as(nombreOriginal).isEqualTo(fuente);
    }

    private static String recetaAdmin(String nombre, int raciones, String ingredientes) {
        return """
                {"nombre": "%s", "categoriaId": "%s", "raciones": %d, "franjasAptas": ["CENA"],
                 "pasos": ["Cocinar todo."], "ingredientes": %s}""".formatted(nombre, CATEGORIA_POLLO, raciones,
                ingredientes);
    }

    private Alimento alimento(String nombre, FuenteDato fuente, String kcal, String proteinas, String hidratos,
            String grasas, List<PorcionCasera> porciones, List<String> alergenos) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_OTROS).orElseThrow());
        alimento.setKcal100(new BigDecimal(kcal));
        alimento.setProteinas100(new BigDecimal(proteinas));
        alimento.setCarbohidratos100(new BigDecimal(hidratos));
        alimento.setGrasas100(new BigDecimal(grasas));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setPorciones(porciones);
        alimento.setAlergenosContiene(alergenos);
        alimento.setAlergenosRevisados(true);
        alimento.setFuente(fuente);
        alimento.setReferenciaExterna(fuente == FuenteDato.USDA ? String.valueOf(170000 + nombre.length()) : "TEST");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setValidado(true);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    private void mapeo(String nombreNormalizado, String nombreAlimento) {
        MapeoIngrediente mapeo = new MapeoIngrediente();
        mapeo.setNombreNormalizado(nombreNormalizado);
        if (nombreAlimento != null) {
            mapeo.setAlimento(alimentoRepositorio.findFirstByNombreAndVisibilidadAndActivoTrue(nombreAlimento,
                    Visibilidad.PUBLICO).orElseThrow());
            mapeo.setMetodo(MetodoMapeo.MANUAL);
            mapeo.setRevisado(true);
        } else {
            mapeo.setMetodo(MetodoMapeo.AUTOMATICO);
            mapeo.setConfianza(new BigDecimal("0.30"));
        }
        mapeoRepositorio.saveAndFlush(mapeo);
    }

    private int auditorias(String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = ?", Integer.class, accion);
    }

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(cuerpo(resultado), "$.id");
    }

    private static String cuerpo(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    private static double numero(String json, String ruta) {
        return ((Number) JsonPath.read(json, ruta)).doubleValue();
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, UsuarioConToken u) {
        return peticion.header("Authorization", u.cabeceraAutorizacion());
    }
}
