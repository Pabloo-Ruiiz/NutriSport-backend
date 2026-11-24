package com.nutrisport.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Perfil deportivo y preferencias alimentarias del deportista autenticado (§7.2). */
@AutoConfigureMockMvc
class PerfilControllerTest extends IntegracionBase {

    private static final String RUTA_PERFIL = "/api/v1/perfil";
    private static final String RUTA_PREFERENCIAS = "/api/v1/perfil/preferencias";
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    private UsuarioConToken deportista;

    @Autowired
    PerfilControllerTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        deportista = crearUsuarioYToken();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Perfil
    // ---------------------------------------------------------------------------------------------

    @Test
    void obtenerSinPerfilDevuelve404PerfilNoEncontrado() throws Exception {
        mockMvc.perform(conToken(get(RUTA_PERFIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERFIL_NO_ENCONTRADO"));
    }

    @Test
    void guardarUnPerfilCorrectoDevuelve200YSePuedeConsultar() throws Exception {
        guardarPerfil(perfil("1995-05-10", "170.5", "GANAR_MUSCULO", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alturaCm").value(170.5))
                .andExpect(jsonPath("$.pesoKg").value(68.4))
                .andExpect(jsonPath("$.objetivo").value("GANAR_MUSCULO"))
                .andExpect(jsonPath("$.objetivosManuales").value(false));

        mockMvc.perform(conToken(get(RUTA_PERFIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaNacimiento").value("1995-05-10"))
                .andExpect(jsonPath("$.deportePrincipal").value("CARRERA_9_7KMH"));
    }

    @Test
    void guardarDosVecesActualizaElMismoPerfil() throws Exception {
        guardarPerfil(perfil("1995-05-10", "170", "MANTENER", null)).andExpect(status().isOk());
        guardarPerfil(perfil("1995-05-10", "171", "COMPETICION", "2027-04-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetivo").value("COMPETICION"))
                .andExpect(jsonPath("$.fechaCompeticion").value("2027-04-12"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil_deportivo", Integer.class)).isEqualTo(1);
    }

    @Test
    void unaAlturaDe300cmDevuelve400ConElCampoAlturaCm() throws Exception {
        guardarPerfil(perfil("1995-05-10", "300", "MANTENER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[0].campo").value("alturaCm"));
    }

    @Test
    void unMenorDe18AnosNoPuedeGuardarElPerfil() throws Exception {
        String haceDiecisieteAnos = LocalDate.now(ZONA).minusYears(17).toString();

        guardarPerfil(perfil(haceDiecisieteAnos, "170", "MANTENER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fechaNacimiento"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil_deportivo", Integer.class)).isZero();
    }

    @Test
    void competicionSinFechaDevuelve400() throws Exception {
        guardarPerfil(perfil("1995-05-10", "170", "COMPETICION", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fechaCompeticion"));
    }

    @Test
    void unDeportePrincipalQueNoEstaEnElCatalogoDevuelve400() throws Exception {
        guardarPerfil(perfil("1995-05-10", "170", "MANTENER", null).replace("CARRERA_9_7KMH", "RUNNING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("deportePrincipal"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM perfil_deportivo", Integer.class)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Preferencias
    // ---------------------------------------------------------------------------------------------

    @Test
    void unaAlergiaSinAlergenoDevuelve400() throws Exception {
        reemplazarPreferencias("""
                {"preferencias": [{"tipo": "ALERGIA", "severidad": "ESTRICTA"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("preferencias[0].alergeno"));
    }

    @Test
    void unaIntoleranciaALaLactosaDevuelve200() throws Exception {
        reemplazarPreferencias("""
                {"preferencias": [{"tipo": "INTOLERANCIA", "intolerancia": "LACTOSA", "severidad": "ESTRICTA"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("INTOLERANCIA"))
                .andExpect(jsonPath("$[0].intolerancia").value("LACTOSA"));
    }

    @Test
    void reemplazarDosVecesDejaSoloLaSegundaLista() throws Exception {
        reemplazarPreferencias("""
                {"preferencias": [
                    {"tipo": "ALERGIA", "alergeno": "GLUTEN", "severidad": "ESTRICTA"},
                    {"tipo": "DIETA", "dieta": "VEGETARIANA", "severidad": "ESTRICTA"}
                ]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        reemplazarPreferencias("""
                {"preferencias": [{"tipo": "ALERGIA", "alergeno": "SESAMO", "severidad": "ESTRICTA"}]}""")
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get(RUTA_PREFERENCIAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].alergeno").value("SESAMO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preferencia_alimentaria", Integer.class)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", deportista.cabeceraAutorizacion());
    }

    private ResultActions guardarPerfil(String cuerpo) throws Exception {
        return mockMvc.perform(conToken(put(RUTA_PERFIL)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions reemplazarPreferencias(String cuerpo) throws Exception {
        return mockMvc.perform(
                conToken(put(RUTA_PREFERENCIAS)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Perfil válido salvo por los valores indicados; {@code fechaCompeticion} nula se omite. */
    private static String perfil(String fechaNacimiento, String alturaCm, String objetivo, String fechaCompeticion) {
        String competicion = fechaCompeticion == null ? "" : "\"fechaCompeticion\": \"%s\",".formatted(fechaCompeticion);
        return """
                {
                  "fechaNacimiento": "%s",
                  "sexo": "MUJER",
                  "alturaCm": %s,
                  "pesoKg": 68.4,
                  "objetivo": "%s",
                  %s
                  "deportePrincipal": "CARRERA_9_7KMH",
                  "nivelActividad": "MODERADO",
                  "diasEntrenamientoSemana": 4,
                  "duracionMediaMin": 60,
                  "comidasPorDia": 4
                }""".formatted(fechaNacimiento, alturaCm, objetivo, competicion);
    }
}
