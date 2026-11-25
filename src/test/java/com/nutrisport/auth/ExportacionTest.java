package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.entidad.TipoAnalisis;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import com.nutrisport.seguimiento.entidad.Insight;
import com.nutrisport.seguimiento.entidad.SeveridadInsight;
import com.nutrisport.seguimiento.entidad.TipoInsight;
import com.nutrisport.seguimiento.repositorio.InsightRepositorio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exportación de todos los datos de la cuenta (GET /api/v1/cuenta/exportacion, §8.3 y §7.1) contra PostgreSQL real:
 * Ana tiene datos en todos los módulos; Luis, en algunos, y no deben aparecer en la exportación de Ana.
 */
@AutoConfigureMockMvc
class ExportacionTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/cuenta/exportacion";
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.now(MADRID);
    private static final LocalDate AYER = HOY.minusDays(1);
    private static final UUID CARNES = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final String REFERENCIA_ALIMENTO = "TEST-EXPORTACION-POLLO";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepositorio usuarioRepositorio;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private AnalisisImagenRepositorio analisisRepositorio;
    @Autowired
    private InsightRepositorio insightRepositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private UsuarioConToken luis;
    private Alimento pollo;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        ana = crearUsuarioYToken();
        luis = crearUsuarioYToken();
        pollo = alimento();
        datosDeAna();
        // Luis también tiene datos: no deben salir en la exportación de Ana
        perfil(luis);
        comida(luis, 500);
        mockMvc.perform(con(post("/api/v1/seguimiento/peso"), luis).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\": \"" + AYER + "\", \"pesoKg\": 80}")).andExpect(status().isCreated());
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE lista_compra, menu_semanal, receta, registro_comida, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa = ?", REFERENCIA_ALIMENTO);
    }

    @Test
    void laExportacionContieneTodasLasSeccionesSinContrasenasNiDatosDeOtros() throws Exception {
        String json = cuerpo(mockMvc.perform(con(get(RUTA), ana))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.startsWith(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nutrisport-datos-"
                        + HOY.format(DateTimeFormatter.BASIC_ISO_DATE) + ".json\"")));

        Map<String, Object> raiz = JsonPath.read(json, "$");
        assertThat(raiz).containsOnlyKeys("fechaExportacion", "cuenta", "perfil", "preferencias", "objetivosDiarios",
                "comidas", "entrenamientos", "pesos", "menus", "listasCompra", "recetasFavoritas", "conversaciones",
                "analisis", "insights");

        // Cuenta y consentimientos, sin contraseña ni tokens
        assertThat(JsonPath.<String>read(json, "$.cuenta.id")).isEqualTo(ana.usuario().getId().toString());
        assertThat(JsonPath.<String>read(json, "$.cuenta.email")).isEqualTo(ana.usuario().getEmail());
        assertThat(JsonPath.<String>read(json, "$.cuenta.consentimientoSaludVersion")).isNotBlank();
        String minusculas = json.toLowerCase(Locale.ROOT);
        assertThat(minusculas).doesNotContain("password").doesNotContain("hash").doesNotContain("token\"")
                .doesNotContain("refresh");
        assertThat(json).doesNotContain(ana.usuario().getPasswordHash());

        // Una entrada (o más) en cada sección, y solo las de Ana
        assertThat(JsonPath.<String>read(json, "$.perfil.objetivo")).isEqualTo("RENDIMIENTO");
        assertThat(JsonPath.<List<Object>>read(json, "$.preferencias")).hasSize(1);
        assertThat(JsonPath.<List<Object>>read(json, "$.objetivosDiarios")).isNotEmpty();
        assertThat(JsonPath.<List<Object>>read(json, "$.comidas")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(json, "$.comidas[0].alimentos[*].nombre"))
                .containsExactly("Pechuga de pollo");
        assertThat(JsonPath.<Double>read(json, "$.comidas[0].alimentos[0].gramos")).isEqualTo(200.0);
        assertThat(JsonPath.<List<Object>>read(json, "$.entrenamientos")).hasSize(1);
        assertThat(JsonPath.<List<Double>>read(json, "$.pesos[*].pesoKg")).containsExactly(61.5);
        assertThat(JsonPath.<List<Object>>read(json, "$.menus")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(json, "$.menus[0].comidas[*].receta")).containsExactly("Pollo asado");
        assertThat(JsonPath.<List<Object>>read(json, "$.listasCompra")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(json, "$.listasCompra[0].productos[*].nombre"))
                .containsExactly("Pechuga de pollo");
        assertThat(JsonPath.<List<String>>read(json, "$.recetasFavoritas[*].nombre")).containsExactly("Pollo asado");
        assertThat(JsonPath.<List<Object>>read(json, "$.conversaciones")).hasSize(1);
        assertThat(JsonPath.<List<String>>read(json, "$.conversaciones[0].mensajes[*].rol"))
                .containsExactly("USUARIO", "ASISTENTE");
        assertThat(JsonPath.<List<Object>>read(json, "$.analisis")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$.analisis[0].candidatos[0].nombreDetectado"))
                .isEqualTo("pechuga de pollo");
        assertThat(JsonPath.<List<String>>read(json, "$.insights[*].tipo")).containsExactly("PROTEINA_BAJA");

        // Ni las imágenes de los análisis ni los datos de Luis
        assertThat(json).doesNotContain("fotos/ana.jpg").doesNotContain("imagenKey").doesNotContain("miniatura");
        assertThat(json).doesNotContain(luis.usuario().getEmail()).doesNotContain("\"pesoKg\":80");
        assertThat(JsonPath.<List<Double>>read(json, "$.comidas[*].alimentos[*].gramos")).doesNotContain(500.0);
    }

    @Test
    void sinEmailVerificadoResponde403() throws Exception {
        Usuario usuario = usuarioRepositorio.findById(ana.usuario().getId()).orElseThrow();
        usuario.setEmailVerificadoEn(null);
        usuarioRepositorio.saveAndFlush(usuario);

        mockMvc.perform(con(get(RUTA), ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NO_VERIFICADO"));
    }

    @Test
    void conElConsentimientoRevocadoSePuedeExportarYUnAdministradorNo() throws Exception {
        mockMvc.perform(con(delete("/api/v1/cuenta/consentimiento-salud"), ana))
                .andExpect(status().isNoContent());
        mockMvc.perform(con(get(RUTA), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuenta.consentimientoSaludRevocadoEn").isNotEmpty())
                .andExpect(jsonPath("$.comidas.length()").value(1));

        mockMvc.perform(con(get(RUTA), crearAdminYToken())).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------

    private void datosDeAna() throws Exception {
        perfil(ana);
        mockMvc.perform(con(put("/api/v1/perfil/preferencias"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferencias\": [{\"tipo\": \"INTOLERANCIA\", \"intolerancia\": \"LACTOSA\", "
                                + "\"severidad\": \"ESTRICTA\"}]}"))
                .andExpect(status().isOk());
        comida(ana, 200);
        mockMvc.perform(con(post("/api/v1/entrenamientos"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoActividadCodigo": "CARRERA_12KMH", "fechaHora": "%s", "duracionMin": 45,
                                 "intensidad": "MEDIA", "estado": "REALIZADO"}"""
                                .formatted(AYER.atTime(18, 0).atZone(MADRID).toOffsetDateTime())))
                .andExpect(status().isCreated());
        mockMvc.perform(con(post("/api/v1/seguimiento/peso"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\": \"" + AYER + "\", \"pesoKg\": 61.5}")).andExpect(status().isCreated());
        // Objetivo del día
        mockMvc.perform(con(get("/api/v1/seguimiento/resumen"), ana).param("fecha", AYER.toString()))
                .andExpect(status().isOk());

        // Receta favorita, menú y lista de la compra
        Receta receta = receta();
        mockMvc.perform(con(put("/api/v1/recetas/" + receta.getId() + "/favorita"), ana))
                .andExpect(status().is2xxSuccessful());
        MenuSemanal menu = new MenuSemanal();
        menu.setUsuario(ana.usuario());
        menu.setNombre("Semana");
        menu.setTipo(TipoMenu.DIARIO);
        menu.setFechaInicio(HOY);
        menu.setFechaFin(HOY);
        menu.setEstado(EstadoMenu.ACTIVO);
        menu.anadirComida(new ComidaPlanificada(HOY, TipoComida.CENA, receta, BigDecimal.ONE));
        menu = menuRepositorio.saveAndFlush(menu);
        mockMvc.perform(con(post("/api/v1/listas-compra/desde-menu/" + menu.getId()), ana))
                .andExpect(status().isCreated());

        // Conversación con el asistente (simulado)
        String conversacion = JsonPath.read(cuerpo(mockMvc.perform(con(post("/api/v1/asistente/conversaciones"),
                ana)).andExpect(status().isCreated())), "$.id");
        mockMvc.perform(con(post("/api/v1/asistente/conversaciones/" + conversacion + "/mensajes"), ana)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contenido\": \"¿Qué ceno hoy?\"}"))
                .andExpect(status().isCreated());

        // Análisis de una foto e insight
        AnalisisImagen analisis = new AnalisisImagen();
        analisis.setUsuario(ana.usuario());
        analisis.setTipo(TipoAnalisis.COMIDA);
        analisis.setEstado(EstadoAnalisis.COMPLETADO);
        analisis.setTipoComida(TipoComida.COMIDA);
        analisis.setImagenKey("fotos/ana.jpg");
        analisis.setMiniaturaKey("fotos/ana-miniatura.jpg");
        analisis.setCandidatos(List.of(new CandidatoAnalisis("pechuga de pollo", null, new BigDecimal("200"),
                new BigDecimal("0.9"), "ALTA", false, List.of())));
        analisisRepositorio.saveAndFlush(analisis);
        Insight insight = new Insight();
        insight.setUsuario(ana.usuario());
        insight.setTipo(TipoInsight.PROTEINA_BAJA);
        insight.setSeveridad(SeveridadInsight.ADVERTENCIA);
        insight.setTitulo("Te falta proteína");
        insight.setTexto("En 4 de tus últimos 7 días…");
        insight.setDatos(Map.of("diasBajos", 4));
        insight.setPeriodoDesde(HOY.minusDays(28));
        insight.setPeriodoHasta(AYER);
        insightRepositorio.saveAndFlush(insight);
    }

    private void perfil(UsuarioConToken usuario) throws Exception {
        mockMvc.perform(con(put("/api/v1/perfil"), usuario).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""))
                .andExpect(status().isOk());
    }

    private void comida(UsuarioConToken usuario, int gramos) throws Exception {
        mockMvc.perform(con(post("/api/v1/comidas"), usuario).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fecha": "%s", "hora": "14:00", "tipoComida": "COMIDA",
                         "alimentos": [{"alimentoId": "%s", "cantidad": %d, "unidad": "g"}]}"""
                        .formatted(AYER, pollo.getId(), gramos)))
                .andExpect(status().isCreated());
    }

    private Receta receta() {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre("Pollo asado");
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setRaciones((short) 1);
        receta.setFranjasAptas(List.of("CENA"));
        RecetaIngrediente ingrediente = new RecetaIngrediente("Pechuga de pollo", "250 g");
        ingrediente.setAlimento(pollo);
        ingrediente.setCantidadGramos(new BigDecimal("250"));
        ingrediente.setEstadoConversion(EstadoConversion.EXACTA);
        receta.anadirIngrediente(ingrediente);
        receta.setEstadoNutricional(EstadoNutricional.COMPLETO);
        receta.setCompletitudPct((short) 100);
        receta.setKcalRacion(new BigDecimal("275"));
        receta.setProteinaRacion(new BigDecimal("58"));
        receta.setCarbohidratosRacion(BigDecimal.ZERO);
        receta.setGrasaRacion(new BigDecimal("4"));
        receta.setAlergenosVerificados(true);
        return recetaRepositorio.saveAndFlush(receta);
    }

    private Alimento alimento() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Pechuga de pollo");
        alimento.setCategoria(categoriaRepositorio.getReferenceById(CARNES));
        alimento.setKcal100(new BigDecimal("110"));
        alimento.setProteinas100(new BigDecimal("23.3"));
        alimento.setCarbohidratos100(BigDecimal.ZERO);
        alimento.setGrasas100(new BigDecimal("1.5"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setFuente(FuenteDato.BEDCA);
        alimento.setAlergenosRevisados(true);
        alimento.setReferenciaExterna(REFERENCIA_ALIMENTO);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion,
            UsuarioConToken usuario) {
        return peticion.header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion());
    }

    private static String cuerpo(ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
