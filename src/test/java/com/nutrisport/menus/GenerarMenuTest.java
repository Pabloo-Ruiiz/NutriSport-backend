package com.nutrisport.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Generación de menús (POST /api/v1/menus/generar) contra PostgreSQL real, con la IA simulada (el adaptador simulado
 * envuelto en un espía para poder hacerlo fallar).
 */
@AutoConfigureMockMvc
class GenerarMenuTest extends IntegracionBase {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.now(MADRID).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final String RUTA = "/api/v1/menus/generar";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private GeneracionTextoPuerto generacionTexto;

    private UsuarioConToken ana;

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE menu_semanal, receta, registro_comida, usuario CASCADE");
    }

    @Test
    void generaUnMenuSemanalConSieteDiasYExplicacion() throws Exception {
        crearPerfil(ana);
        recetasDePrueba(true);
        entrenar(ana, LUNES.plusDays(2));

        String json = respuesta(generar(ana, "SEMANAL", LUNES, null, null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern("/api/v1/menus/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.tipo").value("SEMANAL"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.generadoAutomaticamente").value(true))
                .andExpect(jsonPath("$.nombre")
                        .value("Menú semanal del " + LUNES.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                // De lunes a domingo
                .andExpect(jsonPath("$.fechaInicio").value(LUNES.toString()))
                .andExpect(jsonPath("$.fechaFin").value(LUNES.plusDays(6).toString()))
                .andExpect(jsonPath("$.dias.length()").value(7))
                // La IA simulada redacta la explicación
                .andExpect(jsonPath("$.explicacion").value(org.hamcrest.Matchers.containsString("Respuesta simulada")))
                .andExpect(jsonPath("$.dias[2].diaEntreno").value(true))
                .andExpect(jsonPath("$.dias[0].diaEntreno").value(false)));

        // 4 comidas al día (perfil) en el orden del reparto, con recetas de la franja y raciones del motor
        for (int dia = 0; dia < 7; dia++) {
            List<String> franjas = JsonPath.read(json, "$.dias[" + dia + "].comidas[*].tipoComida");
            assertThat(franjas).as("día %d", dia).containsExactly("DESAYUNO", "COMIDA", "MERIENDA", "CENA");
            List<Object> raciones = JsonPath.read(json, "$.dias[" + dia + "].comidas[*].raciones");
            assertThat(raciones).allSatisfy(r -> assertThat(new BigDecimal(r.toString()))
                    .isIn(new BigDecimal("0.75"), BigDecimal.ONE, new BigDecimal("1.25"), new BigDecimal("1.5"),
                            new BigDecimal("2")));
            assertThat((Integer) JsonPath.read(json, "$.dias[" + dia + "].porcentajeObjetivo")).isPositive();
        }
        // La receta sin publicar no se usa nunca
        List<String> nombres = JsonPath.read(json, "$.dias[*].comidas[*].receta.nombre");
        assertThat(nombres).hasSize(28).doesNotContain("Sin publicar");

        // Guardado
        MenuSemanal guardado = menuRepositorio.findAll().get(0);
        assertThat(guardado.getEstado()).isEqualTo(EstadoMenu.ACTIVO);
        assertThat(guardado.isGeneradoAutomaticamente()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comida_planificada", Long.class)).isEqualTo(28);

        // A la IA solo le llegan datos ya calculados (y nada personal)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(generacionTexto).generar(eq("explicacion-menu"), variables.capture());
        assertThat(variables.getValue()).containsOnlyKeys("tipo", "dias", "objetivoMedioKcal", "mediaKcal",
                "ajustePorcentaje", "diasEntreno", "restricciones", "diasConAviso");
        assertThat(variables.getValue().toString()).doesNotContain(ana.usuario().getEmail())
                .doesNotContain(ana.usuario().getNombre());
        assertThat(variables.getValue()).containsEntry("restricciones", "ninguna").containsEntry("dias", 7);
    }

    @Test
    void siLaIaFallaElMenuSeGeneraConUnaExplicacionDePlantilla() throws Exception {
        crearPerfil(ana);
        recetasDePrueba(true);
        doThrow(new ServicioNoDisponibleException("El servicio de IA no está disponible"))
                .when(generacionTexto).generar(eq("explicacion-menu"), anyMap());

        generar(ana, "SEMANAL", LUNES, "Semana de carga", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Semana de carga"))
                .andExpect(jsonPath("$.dias.length()").value(7))
                .andExpect(jsonPath("$.explicacion").value(org.hamcrest.Matchers.startsWith("Menú semanal de 7 días")))
                .andExpect(jsonPath("$.explicacion").value(
                        org.hamcrest.Matchers.containsString("No tienes restricciones estrictas")));
        assertThat(menuRepositorio.count()).isEqualTo(1);
    }

    @Test
    void sinRecetasCompatiblesResponde422() throws Exception {
        crearPerfil(ana);
        // Ninguna receta tiene los alérgenos verificados, y el usuario es alérgico al gluten
        recetasDePrueba(false);
        mockMvc.perform(conToken(put("/api/v1/perfil/preferencias"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferencias": [{"tipo": "ALERGIA", "alergeno": "GLUTEN", "severidad": "ESTRICTA"}]}"""))
                .andExpect(status().isOk());

        generar(ana, "SEMANAL", LUNES, null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SIN_RECETAS_COMPATIBLES"));

        // Tampoco sin ninguna receta
        jdbc.execute("TRUNCATE receta CASCADE");
        generar(ana, "DIARIO", LUNES, null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SIN_RECETAS_COMPATIBLES"));
        assertThat(menuRepositorio.count()).isZero();
    }

    @Test
    void unMenuDiarioTieneUnDiaYUnoSemanalQueNoEmpiezaEnLunesSieteDiasSeguidos() throws Exception {
        crearPerfil(ana);
        recetasDePrueba(true);

        generar(ana, "DIARIO", LUNES.plusDays(2), null, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DIARIO"))
                .andExpect(jsonPath("$.fechaFin").value(LUNES.plusDays(2).toString()))
                .andExpect(jsonPath("$.dias.length()").value(1));
        generar(ana, "SEMANAL", LUNES.plusDays(3), null, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaInicio").value(LUNES.plusDays(3).toString()))
                .andExpect(jsonPath("$.fechaFin").value(LUNES.plusDays(9).toString()))
                .andExpect(jsonPath("$.dias.length()").value(7));
    }

    @Test
    void validaLaPeticionYExigePerfil() throws Exception {
        recetasDePrueba(true);
        // Sin perfil
        generar(ana, "SEMANAL", LUNES, null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PERFIL_REQUERIDO"));

        crearPerfil(ana);
        // Fecha pasada, tipo ausente o desconocido, nombre en blanco
        generar(ana, "SEMANAL", LocalDate.now(MADRID).minusDays(1), null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fechaInicio"));
        for (String cuerpo : List.of("{\"fechaInicio\": \"" + LUNES + "\"}",
                "{\"tipo\": \"MENSUAL\", \"fechaInicio\": \"" + LUNES + "\"}",
                "{\"tipo\": \"DIARIO\"}",
                "{\"tipo\": \"DIARIO\", \"fechaInicio\": \"" + LUNES + "\", \"nombre\": \"  \"}")) {
            mockMvc.perform(conToken(post(RUTA), ana).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        // Hoy sí vale
        generar(ana, "DIARIO", LocalDate.now(MADRID), null, false).andExpect(status().isCreated());
        // Solo deportistas
        generar(crearAdminYToken(), "DIARIO", LUNES, null, false).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------

    private ResultActions generar(UsuarioConToken usuario, String tipo, LocalDate inicio, String nombre,
            Boolean conExplicacion) throws Exception {
        StringBuilder cuerpo = new StringBuilder("{\"tipo\": \"" + tipo + "\", \"fechaInicio\": \"" + inicio + "\"");
        if (nombre != null) {
            cuerpo.append(", \"nombre\": \"").append(nombre).append('"');
        }
        if (conExplicacion != null) {
            cuerpo.append(", \"conExplicacion\": ").append(conExplicacion);
        }
        return mockMvc.perform(conToken(post(RUTA), usuario).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.append('}').toString()));
    }

    /**
     * 3 desayunos, 5 platos de comida o cena y 3 meriendas publicados y calculados, y uno sin publicar. Con
     * {@code verificados = false} ninguno tiene los alérgenos verificados.
     */
    private void recetasDePrueba(boolean verificados) {
        String[][] datos = {
            // nombre, franjas, kcal, proteína, HC, grasa
            {"Gachas de avena con fruta", "DESAYUNO", "520", "22", "80", "12"},
            {"Tostadas con tomate y aceite", "DESAYUNO", "480", "15", "70", "15"},
            {"Tortitas de plátano", "DESAYUNO", "560", "25", "85", "13"},
            {"Pollo con arroz", "COMIDA,CENA", "700", "50", "80", "18"},
            {"Lentejas estofadas", "COMIDA,CENA", "650", "35", "90", "15"},
            {"Salmón con patata", "COMIDA,CENA", "720", "45", "60", "30"},
            {"Pasta con atún", "COMIDA,CENA", "760", "42", "100", "20"},
            {"Tortilla con ensalada", "COMIDA,CENA", "600", "38", "40", "30"},
            {"Yogur con nueces", "MERIENDA", "320", "15", "25", "18"},
            {"Batido de proteínas", "MERIENDA", "300", "30", "30", "6"},
            {"Bocadillo de pavo", "MERIENDA", "350", "22", "45", "8"},
        };
        for (String[] fila : datos) {
            receta(fila[0], List.of(fila[1].split(",")), fila[2], fila[3], fila[4], fila[5], true, verificados);
        }
        receta("Sin publicar", List.of("DESAYUNO", "COMIDA", "MERIENDA", "CENA"), "500", "40", "50", "15", false,
                verificados);
    }

    private void receta(String nombre, List<String> franjas, String kcal, String proteina, String carbohidratos,
            String grasa, boolean publicada, boolean verificados) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(publicada);
        receta.setFranjasAptas(franjas);
        receta.setEstadoNutricional(EstadoNutricional.COMPLETO);
        receta.setCompletitudPct((short) 100);
        receta.setKcalRacion(new BigDecimal(kcal));
        receta.setProteinaRacion(new BigDecimal(proteina));
        receta.setCarbohidratosRacion(new BigDecimal(carbohidratos));
        receta.setGrasaRacion(new BigDecimal(grasa));
        receta.setAlergenosVerificados(verificados);
        receta.setAptoVegetariano(true);
        recetaRepositorio.saveAndFlush(receta);
    }

    private void crearPerfil(UsuarioConToken usuario) throws Exception {
        mockMvc.perform(conToken(put("/api/v1/perfil"), usuario).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""))
                .andExpect(status().isOk());
    }

    private void entrenar(UsuarioConToken usuario, LocalDate dia) throws Exception {
        String fechaHora = dia.atTime(18, 0).atZone(MADRID).toOffsetDateTime().toString();
        mockMvc.perform(conToken(post("/api/v1/entrenamientos"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoActividadCodigo": "CARRERA_12KMH", "fechaHora": "%s", "duracionMin": 60,
                                 "intensidad": "MEDIA", "estado": "PLANIFICADO"}""".formatted(fechaHora)))
                .andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion,
            UsuarioConToken usuario) {
        return peticion.header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion());
    }

    private static String respuesta(ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
