package com.nutrisport.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.config.IaProperties;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.UsoIaServicio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
 * API del asistente (§7.9) con la IA en modo simulado: el asistente simulado consulta la herramienta real
 * {@code resumenDiario} cuando se le pregunta por la proteína. Ayer, Ana comió 200 g de pechuga de pollo (46,6 g de
 * proteína).
 */
@AutoConfigureMockMvc
class AsistenteControllerTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/asistente";
    private static final LocalDate AYER = LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(1);
    private static final String REFERENCIA_ALIMENTO = "TEST-ASISTENTE-POLLO";
    private static final String PREGUNTA = "¿Cuánta proteína tomé ayer?";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private UsoIaServicio usoIaServicio;
    @Autowired
    private IaProperties iaProperties;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private UsuarioConToken luis;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        ana = crearUsuarioYToken();
        luis = crearUsuarioYToken();
        mockMvc.perform(con(put("/api/v1/perfil"), ana).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""))
                .andExpect(status().isOk());
        mockMvc.perform(con(post("/api/v1/comidas"), ana).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fecha": "%s", "hora": "14:00", "tipoComida": "COMIDA",
                         "alimentos": [{"alimentoId": "%s", "cantidad": 200, "unidad": "g"}]}"""
                        .formatted(AYER, alimento().getId())))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE registro_comida, uso_ia, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa = ?", REFERENCIA_ALIMENTO);
    }

    @Test
    void preguntarPorLaProteinaDeAyerDaLaCifraExactaDelResumen() throws Exception {
        String proteinaAyer = JsonPath.read(cuerpo(mockMvc.perform(con(get("/api/v1/seguimiento/resumen"), ana)
                .param("fecha", AYER.toString()))), "$.consumido.proteina").toString();
        assertThat(proteinaAyer).isEqualTo("46.6");

        String conversacion = cuerpo(mockMvc.perform(con(post(RUTA + "/conversaciones"), ana))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern(RUTA + "/conversaciones/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.titulo").doesNotExist())
                .andExpect(jsonPath("$.mensajes.length()").value(0)));
        String id = JsonPath.read(conversacion, "$.id");

        String respuesta = cuerpo(enviar(ana, id, PREGUNTA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ASISTENTE")));
        // La cifra exacta de ayer (con coma decimal) y las herramientas que la han consultado
        assertThat(JsonPath.<String>read(respuesta, "$.contenido")).contains("46,6 g de proteína");
        assertThat(JsonPath.<List<String>>read(respuesta, "$.herramientasUsadas")).isNotEmpty()
                .contains("resumenDiario");

        // El título se rellena con el primer mensaje y la conversación guarda los dos mensajes
        mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value(PREGUNTA))
                .andExpect(jsonPath("$.mensajes[*].rol").value(org.hamcrest.Matchers.contains("USUARIO", "ASISTENTE")))
                .andExpect(jsonPath("$.mensajes[0].contenido").value(PREGUNTA))
                .andExpect(jsonPath("$.mensajes[0].herramientasUsadas.length()").value(0));
        // Se guardan las herramientas y se registra el uso
        assertThat(jdbc.queryForObject("SELECT herramientas_usadas::text FROM mensaje_ia WHERE rol = 'ASISTENTE'",
                String.class)).contains("resumenDiario");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uso_ia WHERE funcion = 'ASISTENTE' AND exito",
                Long.class)).isEqualTo(1);

        // Un segundo mensaje no cambia el título; el listado lleva la vista previa del último
        enviar(ana, id, "¿qué ceno hoy?").andExpect(status().isCreated());
        mockMvc.perform(con(get(RUTA + "/conversaciones"), ana))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value(PREGUNTA))
                .andExpect(jsonPath("$.content[0].vistaPrevia").value(org.hamcrest.Matchers.startsWith(
                        "Soy el asistente en modo simulado")));
    }

    @Test
    void unTemaSensibleRecibeUnaRespuestaSeguraSinLlamarAlModelo() throws Exception {
        String id = nuevaConversacion(ana);

        String respuesta = cuerpo(enviar(ana, id, "¿cuántos mg de ibuprofeno tomo después de correr?")
                .andExpect(status().isCreated()));

        assertThat(JsonPath.<String>read(respuesta, "$.contenido")).contains("112").contains("dosis");
        assertThat(JsonPath.<List<String>>read(respuesta, "$.herramientasUsadas")).isEmpty();
        // No cuenta como uso de la IA
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uso_ia", Long.class)).isZero();
    }

    @Test
    void lasConversacionesYLosMensajesDeOtroUsuarioResponden404() throws Exception {
        String id = nuevaConversacion(ana);
        String respuestaId = JsonPath.read(cuerpo(enviar(ana, id, PREGUNTA)), "$.id");

        mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), luis)).andExpect(status().isNotFound());
        enviar(luis, id, PREGUNTA).andExpect(status().isNotFound());
        mockMvc.perform(con(patch(RUTA + "/conversaciones/" + id), luis).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\": \"Mía\"}")).andExpect(status().isNotFound());
        mockMvc.perform(con(delete(RUTA + "/conversaciones/" + id), luis)).andExpect(status().isNotFound());
        valorar(luis, respuestaId, 1).andExpect(status().isNotFound());
        mockMvc.perform(con(get(RUTA + "/conversaciones"), luis)).andExpect(jsonPath("$.content.length()").value(0));

        // Para Ana sigue igual
        mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes.length()").value(2));
    }

    @Test
    void superarElLimiteDiarioDeMensajesResponde429() throws Exception {
        String id = nuevaConversacion(ana);
        for (int i = 0; i < iaProperties.limiteMensajesDiario(); i++) {
            usoIaServicio.registrar(ana.usuario().getId(), FuncionIa.ASISTENTE, new ResultadoIa<>("x", 1, 1, "m"), 1,
                    true);
        }

        enviar(ana, id, PREGUNTA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").exists());

        // No se guarda el mensaje ni se llama al asistente
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje_ia", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uso_ia", Long.class))
                .isEqualTo(iaProperties.limiteMensajesDiario());
        // El límite es de cada usuario
        enviar(luis, nuevaConversacion(luis), "¿qué ceno hoy?").andExpect(status().isCreated());
    }

    @Test
    void renombrarArchivarValorarYEliminar() throws Exception {
        String id = nuevaConversacion(ana);
        String respuesta = cuerpo(enviar(ana, id, PREGUNTA));
        String respuestaId = JsonPath.read(respuesta, "$.id");
        String preguntaId = JsonPath.read(cuerpo(mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), ana))),
                "$.mensajes[0].id");

        mockMvc.perform(con(patch(RUTA + "/conversaciones/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"Proteína\", \"archivada\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Proteína"))
                .andExpect(jsonPath("$.archivada").value(true));
        mockMvc.perform(con(get(RUTA + "/conversaciones"), ana).param("archivada", "false"))
                .andExpect(jsonPath("$.content.length()").value(0));

        valorar(ana, respuestaId, -1).andExpect(status().isNoContent());
        valorar(ana, respuestaId, 1).andExpect(status().isNoContent());
        mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), ana))
                .andExpect(jsonPath("$.mensajes[1].valoracion").value(1));
        // Solo las respuestas del asistente, y solo −1 o 1
        valorar(ana, preguntaId, 1).andExpect(status().isUnprocessableEntity());
        valorar(ana, respuestaId, 0).andExpect(status().isBadRequest());
        enviar(ana, id, " ").andExpect(status().isBadRequest());

        mockMvc.perform(con(get(RUTA + "/sugerencias"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(3), org.hamcrest.Matchers.lessThanOrEqualTo(4))));

        mockMvc.perform(con(delete(RUTA + "/conversaciones/" + id), ana)).andExpect(status().isNoContent());
        mockMvc.perform(con(get(RUTA + "/conversaciones/" + id), ana)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje_ia", Long.class)).isZero();
    }

    @Test
    void conElConsentimientoRevocadoResponde403() throws Exception {
        mockMvc.perform(con(delete("/api/v1/cuenta/consentimiento-salud"), ana))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(con(get(RUTA + "/conversaciones"), ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REVOCADO"));
    }

    // ---------------------------------------------------------------------------------------------

    private String nuevaConversacion(UsuarioConToken usuario) throws Exception {
        return JsonPath.read(cuerpo(mockMvc.perform(con(post(RUTA + "/conversaciones"), usuario))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions enviar(UsuarioConToken usuario, String conversacionId, String contenido) throws Exception {
        return mockMvc.perform(con(post(RUTA + "/conversaciones/" + conversacionId + "/mensajes"), usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contenido\": \"" + contenido + "\"}"));
    }

    private ResultActions valorar(UsuarioConToken usuario, String mensajeId, int valor) throws Exception {
        return mockMvc.perform(con(post(RUTA + "/mensajes/" + mensajeId + "/valoracion"), usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\": " + valor + "}"));
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion,
            UsuarioConToken usuario) {
        return peticion.header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion());
    }

    private static String cuerpo(ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Alimento alimento() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Pechuga de pollo");
        alimento.setCategoria(categoriaRepositorio.getReferenceById(
                UUID.fromString("c0000000-0000-0000-0000-000000000003")));
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
}
