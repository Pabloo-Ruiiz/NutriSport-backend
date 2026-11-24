package com.nutrisport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Flujo completo del MVP de principio a fin, solo con la API HTTP: registro y verificación, perfil y
 * objetivos, preferencias, comida manual, foto analizada por la IA (simulada) y confirmada, entrenamiento,
 * resumen del día, consentimiento de salud y eliminación de la cuenta.
 * <p>
 * Sin {@code @Transactional}: cada petición confirma su transacción, así que se ejecutan de verdad los
 * eventos AFTER_COMMIT (envío de correos y procesado del análisis, síncrono en los tests). Los datos se
 * borran al terminar. El correo se sustituye por un mock para capturar el token de verificación.
 * <p>
 * El catálogo lo crea un administrador por la API (en los tests no se cargan los datos de desarrollo).
 * Las funcionalidades de la Ampliación tendrán sus propios tests.
 */
@AutoConfigureMockMvc
class FlujoCompletoTest extends IntegracionBase {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final UUID CATEGORIA_CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");

    private static final String NOMBRE = "Álex";
    private static final String EMAIL = "alex.flujo@ejemplo.com";
    private static final String PASSWORD = "MediaMaraton2026";
    private static final String DEPORTE = "CARRERA_12KMH";

    @MockitoBean
    private CorreoServicio correoServicio;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final LimitadorPeticiones limitador;

    private LocalDate hoy;
    /** Access token de Álex tras el login. */
    private String token;

    @Autowired
    FlujoCompletoTest(MockMvc mockMvc, JdbcTemplate jdbc, LimitadorPeticiones limitador) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.limitador = limitador;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        limitador.reiniciarTodo();
        hoy = LocalDate.now(ZONA);
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
        limitador.reiniciarTodo();
    }

    @Test
    void flujoCompletoDelMvp() throws Exception {
        Map<String, String> catalogo = crearCatalogoComoAdmin();

        // 1. Registro, verificación del email con el token del correo y login
        registrarVerificarYEntrar();

        // 2. Perfil y objetivos calculados
        guardarPerfilYComprobarObjetivos();

        // 3. Preferencia estricta: intolerancia a la lactosa
        anadirIntoleranciaLactosa();

        // 4. Desayuno manual con avena y plátano
        String desayuno = registrarDesayuno(catalogo);

        // 5. Foto analizada por la IA y confirmada corrigiendo los gramos de un alimento
        String cena = analizarYConfirmarCena();

        // 6. Entrenamiento de 65 minutos: el objetivo del día sube
        BigDecimal kcalObjetivoAntes = numero(resumen(), "$.objetivo.kcal");
        registrarEntrenamiento();
        String resumen = resumen();
        assertThat(numero(resumen, "$.kcalEntreno")).isPositive();
        assertThat(numero(resumen, "$.objetivo.kcal")).isGreaterThan(kcalObjetivoAntes);

        // 7. Resumen: consumido = desayuno + cena; objetivo = ObjetivoDiario del día
        comprobarResumen(resumen, desayuno, cena);

        // 8. Revocar el consentimiento bloquea los datos de salud; volver a darlo los desbloquea
        revocarYVolverADarConsentimiento();

        // 9. Eliminar la cuenta: el login falla y el token deja de servir
        eliminarCuentaYComprobarLogin();
    }

    // ---------------------------------------------------------------------------------------------
    // Pasos
    // ---------------------------------------------------------------------------------------------

    private void registrarVerificarYEntrar() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre": "%s", "email": "%s", "password": "%s",
                         "mayorDeEdad": true, "aceptaTerminos": true, "consentimientoSalud": true}"""
                        .formatted(NOMBRE, EMAIL, PASSWORD)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> tokenVerificacion = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarVerificacion(eq(EMAIL), eq(NOMBRE), tokenVerificacion.capture());

        mockMvc.perform(post("/api/v1/auth/verificar-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(tokenVerificacion.getValue())))
                .andExpect(status().isNoContent());

        String login = respuesta(login(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.cuenta.nombre").value(NOMBRE))
                .andExpect(jsonPath("$.cuenta.emailVerificado").value(true))
                .andExpect(jsonPath("$.cuenta.consentimientoSaludVigente").value(true)));
        token = JsonPath.read(login, "$.accessToken");
    }

    private void guardarPerfilYComprobarObjetivos() throws Exception {
        String fechaNacimiento = hoy.minusYears(30).toString();
        mockMvc.perform(conToken(put("/api/v1/perfil")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "%s", "sexo": "HOMBRE", "alturaCm": 178, "pesoKg": 71.4,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "%s", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""
                        .formatted(fechaNacimiento, DEPORTE)))
                .andExpect(status().isOk());

        String objetivos = respuesta(mockMvc.perform(conToken(get("/api/v1/perfil/objetivos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manuales").value(false)));
        assertThat(numero(objetivos, "$.kcal")).isPositive();
        assertThat(numero(objetivos, "$.proteinaG")).isPositive();
        assertThat(numero(objetivos, "$.carbohidratosG")).isPositive();
        assertThat(numero(objetivos, "$.grasaG")).isPositive();
    }

    private void anadirIntoleranciaLactosa() throws Exception {
        mockMvc.perform(conToken(put("/api/v1/perfil/preferencias")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferencias": [{"tipo": "INTOLERANCIA", "intolerancia": "LACTOSA",
                                                   "severidad": "ESTRICTA"}]}"""))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get("/api/v1/perfil/preferencias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("INTOLERANCIA"))
                .andExpect(jsonPath("$[0].intolerancia").value("LACTOSA"))
                .andExpect(jsonPath("$[0].severidad").value("ESTRICTA"));
    }

    private String registrarDesayuno(Map<String, String> catalogo) throws Exception {
        return respuesta(mockMvc.perform(conToken(post("/api/v1/comidas")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "%s", "hora": "08:00", "tipoComida": "DESAYUNO",
                                 "alimentos": [{"alimentoId": "%s", "cantidad": 60, "unidad": "g"},
                                               {"alimentoId": "%s", "cantidad": 120, "unidad": "g"}]}"""
                                .formatted(hoy, catalogo.get("Copos de avena"), catalogo.get("Plátano"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.alimentos.length()").value(2))
                // Ni la avena ni el plátano tienen lactosa: la intolerancia no genera avisos
                .andExpect(jsonPath("$.avisos").isEmpty()));
    }

    private String analizarYConfirmarCena() throws Exception {
        String location = mockMvc.perform(multipart("/api/v1/analisis/imagenes")
                        .file(new MockMultipartFile("imagen", "cena.png", "image/png", png()))
                        .param("tipoComida", "CENA")
                        .param("descripcion", "salmón con arroz, brócoli y aguacate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();

        String analisis = esperarFinDelAnalisis(location);
        assertThat((String) JsonPath.read(analisis, "$.estado")).isEqualTo("COMPLETADO");

        // Se acepta la primera coincidencia de cada candidato; el arroz se corrige de 180 g a 150 g
        List<Map<String, Object>> candidatos = JsonPath.read(analisis, "$.candidatos");
        assertThat(candidatos).hasSize(4);
        List<String> alimentos = new ArrayList<>();
        for (int i = 0; i < candidatos.size(); i++) {
            String alimentoId = JsonPath.read(analisis, "$.candidatos[%d].coincidencias[0].alimentoId".formatted(i));
            String nombre = JsonPath.read(analisis, "$.candidatos[%d].nombreDetectado".formatted(i));
            BigDecimal gramos = numero(analisis, "$.candidatos[%d].gramosEstimados".formatted(i));
            if (nombre.equals("Arroz blanco")) {
                assertThat(gramos).isEqualByComparingTo("180");
                gramos = new BigDecimal("150");
            }
            alimentos.add("{\"alimentoId\": \"%s\", \"gramos\": %s, \"indiceCandidato\": %d}"
                    .formatted(alimentoId, gramos.toPlainString(), i));
        }

        String cena = respuesta(mockMvc.perform(conToken(post(location + "/confirmar"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\": \"%s\", \"hora\": \"21:00\", \"tipoComida\": \"CENA\", \"alimentos\": [%s]}"
                                .formatted(hoy, String.join(", ", alimentos))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("FOTO"))
                .andExpect(jsonPath("$.tipoComida").value("CENA"))
                .andExpect(jsonPath("$.alimentos.length()").value(4)));

        mockMvc.perform(conToken(get(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
        return cena;
    }

    private void registrarEntrenamiento() throws Exception {
        String fechaHora = hoy.atTime(LocalTime.NOON).atZone(ZONA).toOffsetDateTime().toString();
        mockMvc.perform(conToken(post("/api/v1/entrenamientos")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipoActividadCodigo": "%s", "fechaHora": "%s", "duracionMin": 65,
                         "intensidad": "MEDIA", "estado": "PLANIFICADO"}""".formatted(DEPORTE, fechaHora)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracionMin").value(65));
    }

    private void comprobarResumen(String resumen, String desayuno, String cena) throws Exception {
        assertThat((String) JsonPath.read(resumen, "$.fecha")).isEqualTo(hoy.toString());
        assertThat((List<?>) JsonPath.read(resumen, "$.comidas")).hasSize(2);
        assertThat((List<?>) JsonPath.read(resumen, "$.entrenamientos")).hasSize(1);

        for (String campo : List.of("kcal", "proteina", "carbohidratos", "grasa")) {
            BigDecimal esperado = numero(desayuno, "$.totales." + campo).add(numero(cena, "$.totales." + campo));
            assertThat(numero(resumen, "$.consumido." + campo)).as("consumido.%s", campo)
                    .isEqualByComparingTo(esperado);
        }

        Map<String, Object> objetivoDiario = jdbc.queryForMap("""
                SELECT o.kcal_total, o.proteina_g, o.carbohidratos_total_g, o.grasa_g, o.kcal_entreno
                  FROM objetivo_diario o JOIN usuario u ON u.id = o.usuario_id
                 WHERE u.email = ? AND o.fecha = ?""", EMAIL, hoy);
        assertThat(numero(resumen, "$.objetivo.kcal")).isEqualByComparingTo(decimal(objetivoDiario.get("kcal_total")));
        assertThat(numero(resumen, "$.objetivo.proteina")).isEqualByComparingTo(decimal(objetivoDiario.get("proteina_g")));
        assertThat(numero(resumen, "$.objetivo.carbohidratos"))
                .isEqualByComparingTo(decimal(objetivoDiario.get("carbohidratos_total_g")));
        assertThat(numero(resumen, "$.objetivo.grasa")).isEqualByComparingTo(decimal(objetivoDiario.get("grasa_g")));
        assertThat(numero(resumen, "$.kcalEntreno")).isEqualByComparingTo(decimal(objetivoDiario.get("kcal_entreno")));
    }

    private void revocarYVolverADarConsentimiento() throws Exception {
        mockMvc.perform(conToken(delete("/api/v1/cuenta/consentimiento-salud")))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(conToken(get("/api/v1/perfil")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REVOCADO"));

        mockMvc.perform(conToken(post("/api/v1/cuenta/consentimiento-salud")))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(conToken(get("/api/v1/perfil")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pesoKg").value(71.4));
    }

    private void eliminarCuentaYComprobarLogin() throws Exception {
        mockMvc.perform(conToken(delete("/api/v1/cuenta")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"%s\", \"inmediata\": false}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        // El access token aún no ha caducado, pero el usuario ya no está activo
        mockMvc.perform(conToken(get("/api/v1/cuenta"))).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** El administrador crea por la API los alimentos públicos que usan el desayuno y la foto. */
    private Map<String, String> crearCatalogoComoAdmin() throws Exception {
        UsuarioConToken admin = crearAdminYToken();
        // nombre, preparación, categoría, kcal, proteína, carbohidratos y grasa por 100 g
        List<List<String>> alimentos = List.of(
                List.of("Copos de avena", "CRUDO", CATEGORIA_CEREALES.toString(), "372", "13.5", "58.7", "7.0"),
                List.of("Plátano", "CRUDO", CATEGORIA_OTROS.toString(), "94", "1.2", "20.0", "0.3"),
                List.of("Salmón", "PLANCHA", CATEGORIA_OTROS.toString(), "208", "20.0", "0.0", "13.0"),
                List.of("Arroz blanco cocido", "COCIDO", CATEGORIA_CEREALES.toString(), "130", "2.7", "28.0", "0.3"),
                List.of("Brócoli", "COCIDO", CATEGORIA_OTROS.toString(), "35", "2.4", "4.4", "0.4"),
                List.of("Aguacate", "CRUDO", CATEGORIA_OTROS.toString(), "160", "2.0", "1.9", "14.7"));
        Map<String, String> ids = new HashMap<>();
        for (List<String> a : alimentos) {
            String creado = respuesta(mockMvc.perform(post("/api/v1/admin/alimentos")
                            .param("fuente", "ADMIN")
                            .header("Authorization", admin.cabeceraAutorizacion())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nombre": "%s", "preparacion": "%s", "categoriaId": "%s", "kcal100": %s,
                                     "proteinas100": %s, "carbohidratos100": %s, "grasas100": %s, "unidadBase": "G",
                                     "contieneLactosa": false, "aptoVegano": %s, "aptoVegetariano": %s,
                                     "aptoPescetariano": true}"""
                                    .formatted(a.get(0), a.get(1), a.get(2), a.get(3), a.get(4), a.get(5), a.get(6),
                                            !a.get(0).equals("Salmón"), !a.get(0).equals("Salmón"))))
                    .andExpect(status().isCreated()));
            ids.put(a.get(0), JsonPath.read(creado, "$.id"));
        }
        return ids;
    }

    /** Consulta el análisis (polling, como el frontend) hasta que deja de estar PENDIENTE o PROCESANDO. */
    private String esperarFinDelAnalisis(String location) throws Exception {
        for (int intento = 0; intento < 50; intento++) {
            String analisis = respuesta(mockMvc.perform(conToken(get(location))).andExpect(status().isOk()));
            String estado = JsonPath.read(analisis, "$.estado");
            if (!estado.equals("PENDIENTE") && !estado.equals("PROCESANDO")) {
                return analisis;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El análisis no ha terminado en 5 segundos");
    }

    private String resumen() throws Exception {
        return respuesta(mockMvc.perform(conToken(get("/api/v1/seguimiento/resumen")).param("fecha", hoy.toString()))
                .andExpect(status().isOk()));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\", \"mantenerSesion\": false}"
                        .formatted(EMAIL, password)));
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    private static String respuesta(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal numero(String json, String ruta) {
        return decimal(JsonPath.read(json, ruta));
    }

    private static BigDecimal decimal(Object valor) {
        return new BigDecimal(String.valueOf(valor));
    }

    /** PNG de 800×600 generado en memoria (la IA simulada no mira su contenido). */
    private static byte[] png() throws IOException {
        BufferedImage imagen = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setColor(new Color(230, 120, 60));
            grafico.fillRect(0, 0, 800, 600);
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
