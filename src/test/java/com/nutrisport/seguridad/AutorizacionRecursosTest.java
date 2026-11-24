package com.nutrisport.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Control de propiedad (§8.2): el usuario A crea un recurso de cada tipo y el usuario B, autenticado y con
 * el rol DEPORTISTA, intenta leerlo, modificarlo y borrarlo. Siempre debe recibir 404 (no 403), para no
 * revelar que el recurso existe, y el recurso de A debe seguir intacto.
 * <p>
 * Se prueban todas las rutas con {@code /{id}} que existen para cada recurso. Algunos recursos no tienen
 * todas las operaciones (no hay {@code GET /entrenamientos/{id}} ni {@code GET|PUT /seguimiento/peso/{id}},
 * y los análisis se modifican con {@code POST /confirmar|/descartar} en lugar de PUT/DELETE).
 * <p>
 * Los cuerpos de PUT y POST son válidos: así el 404 sale del control de propiedad y no de la validación.
 * <p>
 * Ampliación: añadir aquí los recursos nuevos (menús, plan semanal, conversaciones del asistente,
 * etiquetas…) en {@link Recurso}, {@link #crearRecursosDeA()} y {@link #casos()}.
 * <p>
 * También comprueba que un deportista recibe 403 en una muestra de rutas de administración.
 */
@AutoConfigureMockMvc
class AutorizacionRecursosTest extends IntegracionBase {

    private static final UUID CATEGORIA_CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");
    private static final String HOY = LocalDate.now(ZoneId.of("Europe/Madrid")).toString();

    /** Tipos de recurso personal del MVP y la tabla donde se guarda cada uno. */
    enum Recurso {
        COMIDA("registro_comida"),
        ANALISIS("analisis_imagen"),
        ENTRENAMIENTO("entrenamiento"),
        PESAJE("registro_peso"),
        ALIMENTO_PRIVADO("alimento");

        private final String tabla;

        Recurso(String tabla) {
            this.tabla = tabla;
        }
    }

    /**
     * Una petición de B sobre un recurso de A.
     *
     * @param ruta   ruta con {@code {id}}, que se sustituye por el id del recurso de A
     * @param cuerpo JSON válido, o {@code null} si la operación no lleva cuerpo
     */
    record Caso(Recurso recurso, HttpMethod metodo, String ruta, String cuerpo) {

        @Override
        public String toString() {
            return (recurso == null ? "" : recurso + ": ") + metodo + " " + ruta;
        }
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    private UsuarioConToken usuarioA;
    private UsuarioConToken usuarioB;
    private final Map<Recurso, String> idsDeA = new EnumMap<>(Recurso.class);

    @Autowired
    AutorizacionRecursosTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void preparar() throws Exception {
        vaciarTablas();
        usuarioA = crearUsuarioYToken();
        usuarioB = crearUsuarioYToken();
        crearRecursosDeA();
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    // ---------------------------------------------------------------------------------------------
    // Recursos ajenos: 404
    // ---------------------------------------------------------------------------------------------

    static Stream<Caso> casos() {
        String alimentoPublicoNoExistente = UUID.randomUUID().toString();
        String comida = """
                {"fecha": "%s", "tipoComida": "CENA",
                 "alimentos": [{"alimentoId": "%s", "cantidad": 100, "unidad": "g"}]}"""
                .formatted(HOY, alimentoPublicoNoExistente);
        String entrenamiento = """
                {"tipoActividadCodigo": "CARRERA_9_7KMH", "fechaHora": "%sT07:00:00+01:00", "duracionMin": 30,
                 "intensidad": "BAJA"}""".formatted(HOY);
        String confirmacion = """
                {"fecha": "%s", "tipoComida": "COMIDA",
                 "alimentos": [{"alimentoId": "%s", "gramos": 100}]}"""
                .formatted(HOY, alimentoPublicoNoExistente);

        return Stream.of(
                new Caso(Recurso.COMIDA, HttpMethod.GET, "/api/v1/comidas/{id}", null),
                new Caso(Recurso.COMIDA, HttpMethod.PUT, "/api/v1/comidas/{id}", comida),
                new Caso(Recurso.COMIDA, HttpMethod.DELETE, "/api/v1/comidas/{id}", null),
                new Caso(Recurso.COMIDA, HttpMethod.POST, "/api/v1/comidas/{id}/duplicar",
                        "{\"fecha\": \"%s\"}".formatted(HOY)),

                new Caso(Recurso.ANALISIS, HttpMethod.GET, "/api/v1/analisis/{id}", null),
                new Caso(Recurso.ANALISIS, HttpMethod.GET, "/api/v1/analisis/{id}/imagen", null),
                new Caso(Recurso.ANALISIS, HttpMethod.POST, "/api/v1/analisis/{id}/confirmar", confirmacion),
                new Caso(Recurso.ANALISIS, HttpMethod.POST, "/api/v1/analisis/{id}/descartar", null),

                new Caso(Recurso.ENTRENAMIENTO, HttpMethod.PUT, "/api/v1/entrenamientos/{id}", entrenamiento),
                new Caso(Recurso.ENTRENAMIENTO, HttpMethod.DELETE, "/api/v1/entrenamientos/{id}", null),

                new Caso(Recurso.PESAJE, HttpMethod.DELETE, "/api/v1/seguimiento/peso/{id}", null),

                new Caso(Recurso.ALIMENTO_PRIVADO, HttpMethod.GET, "/api/v1/alimentos/{id}", null),
                new Caso(Recurso.ALIMENTO_PRIVADO, HttpMethod.PUT, "/api/v1/alimentos/{id}",
                        alimento("Avena de B")),
                new Caso(Recurso.ALIMENTO_PRIVADO, HttpMethod.DELETE, "/api/v1/alimentos/{id}", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void otroUsuarioRecibe404YElRecursoNoCambia(Caso caso) throws Exception {
        String id = idsDeA.get(caso.recurso());
        MockHttpServletRequestBuilder peticion = request(caso.metodo(), caso.ruta(), id)
                .header("Authorization", usuarioB.cabeceraAutorizacion());
        if (caso.cuerpo() != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(caso.cuerpo());
        }

        mockMvc.perform(peticion)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM " + caso.recurso().tabla + " WHERE id = ?::uuid", Integer.class, id);
        assertThat(filas).as("el recurso de A sigue existiendo").isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------------
    // Rutas de administración: 403 para un deportista
    // ---------------------------------------------------------------------------------------------

    static Stream<Caso> rutasAdmin() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                new Caso(null, HttpMethod.GET, "/api/v1/admin/usuarios", null),
                new Caso(null, HttpMethod.GET, "/api/v1/admin/usuarios/" + id, null),
                new Caso(null, HttpMethod.PATCH, "/api/v1/admin/usuarios/" + id, "{\"estado\": \"BLOQUEADO\"}"),
                new Caso(null, HttpMethod.GET, "/api/v1/admin/alimentos", null),
                new Caso(null, HttpMethod.PUT, "/api/v1/admin/alimentos/" + id, alimento("Avena")),
                new Caso(null, HttpMethod.DELETE, "/api/v1/admin/alimentos/" + id, null),
                new Caso(null, HttpMethod.PUT, "/api/v1/admin/alimentos/" + id + "/alergenos",
                        "{\"alergenosContiene\": [], \"alergenosTrazas\": [], \"contieneLactosa\": false}"),
                new Caso(null, HttpMethod.POST, "/api/v1/admin/categorias",
                        "{\"nombre\": \"Nueva\", \"tipo\": \"ALIMENTO\", \"orden\": 1}"),
                new Caso(null, HttpMethod.DELETE, "/api/v1/admin/categorias/" + id, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rutasAdmin")
    void deportistaRecibe403EnLasRutasDeAdministracion(Caso caso) throws Exception {
        MockHttpServletRequestBuilder peticion = request(caso.metodo(), caso.ruta())
                .header("Authorization", usuarioB.cabeceraAutorizacion());
        if (caso.cuerpo() != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(caso.cuerpo());
        }

        mockMvc.perform(peticion)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    // ---------------------------------------------------------------------------------------------
    // Preparación: A crea sus recursos por la API
    // ---------------------------------------------------------------------------------------------

    private void crearRecursosDeA() throws Exception {
        String alimentoId = idDe(comoA(post("/api/v1/alimentos"), alimento("Avena de A")));
        idsDeA.put(Recurso.ALIMENTO_PRIVADO, alimentoId);

        idsDeA.put(Recurso.COMIDA, idDe(comoA(post("/api/v1/comidas"), """
                {"fecha": "%s", "tipoComida": "DESAYUNO",
                 "alimentos": [{"alimentoId": "%s", "cantidad": 60, "unidad": "g"}]}"""
                .formatted(HOY, alimentoId))));

        idsDeA.put(Recurso.ANALISIS, idDe(mockMvc.perform(multipart("/api/v1/analisis/imagenes")
                .file(new MockMultipartFile("imagen", "comida.png", "image/png", png()))
                .param("tipoComida", "COMIDA")
                .header("Authorization", usuarioA.cabeceraAutorizacion()))));

        // Registrar entrenamientos exige perfil (el gasto se calcula con el peso)
        comoA(put("/api/v1/perfil"), """
                {"fechaNacimiento": "1995-05-10", "sexo": "MUJER", "alturaCm": 170, "pesoKg": 68.4,
                 "objetivo": "MANTENER", "deportePrincipal": "CARRERA_9_7KMH", "nivelActividad": "MODERADO",
                 "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}""")
                .andExpect(status().is2xxSuccessful());
        idsDeA.put(Recurso.ENTRENAMIENTO, idDe(comoA(post("/api/v1/entrenamientos"), """
                {"tipoActividadCodigo": "CARRERA_9_7KMH", "fechaHora": "%sT07:00:00+01:00", "duracionMin": 60,
                 "intensidad": "MEDIA", "estado": "PLANIFICADO"}""".formatted(HOY))));

        idsDeA.put(Recurso.PESAJE, idDe(comoA(post("/api/v1/seguimiento/peso"),
                "{\"fecha\": \"%s\", \"pesoKg\": 68.1}".formatted(HOY))));

        assertThat(idsDeA).containsOnlyKeys(Recurso.values());
    }

    private ResultActions comoA(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return mockMvc.perform(peticion
                .header("Authorization", usuarioA.cabeceraAutorizacion())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** AlimentoRequest válido con el nombre indicado. */
    private static String alimento(String nombre) {
        return """
                {"nombre": "%s", "categoriaId": "%s", "kcal100": 370, "proteinas100": 13.5,
                 "carbohidratos100": 58.7, "grasas100": 7.0, "unidadBase": "G", "contieneLactosa": false,
                 "aptoVegano": true, "aptoVegetariano": true, "aptoPescetariano": true}"""
                .formatted(nombre, CATEGORIA_CEREALES);
    }

    /** PNG pequeño generado en memoria. */
    private static byte[] png() throws IOException {
        BufferedImage imagen = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setColor(new Color(230, 120, 60));
            grafico.fillRect(0, 0, 400, 300);
        } finally {
            grafico.dispose();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    /** Las categorías y los tipos de actividad iniciales (migraciones repetibles) se conservan. */
    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
