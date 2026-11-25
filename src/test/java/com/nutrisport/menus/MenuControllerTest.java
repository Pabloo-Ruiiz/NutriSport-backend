package com.nutrisport.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

/** Lectura, cambios y borrado de menús (GET/PATCH/DELETE /api/v1/menus) contra PostgreSQL real. */
@AutoConfigureMockMvc
class MenuControllerTest extends IntegracionBase {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** El lunes que viene: los entrenamientos de esa semana pueden estar PLANIFICADOS. */
    private static final LocalDate LUNES = LocalDate.now(MADRID).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private Receta avena;
    private Receta pollo;
    private Receta sinCalculo;

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken();
        avena = receta("Gachas de avena", EstadoNutricional.COMPLETO, "400", "20");
        pollo = receta("Pollo con arroz", EstadoNutricional.APROXIMADO, "600", "45");
        sinCalculo = receta("Guiso sin calcular", EstadoNutricional.INCOMPLETO, "999", "99");
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE menu_semanal, receta, registro_comida, usuario CASCADE");
    }

    @Test
    void elDetalleCalculaCadaDiaFrenteASuObjetivo() throws Exception {
        crearPerfil(ana);
        entrenar(ana, LUNES.plusDays(1), "PLANIFICADO");
        entrenar(ana, LUNES.plusDays(2), "OMITIDO");
        UUID id = menu(ana.usuario(), "Semana de carga", LUNES, LUNES.plusDays(2), false, m -> {
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE));
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, new BigDecimal("1.5")));
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.CENA, sinCalculo, BigDecimal.ONE));
            m.anadirComida(new ComidaPlanificada(LUNES.plusDays(1), TipoComida.COMIDA, pollo, new BigDecimal("2")));
        });

        String json = respuesta(mockMvc.perform(conToken(get("/api/v1/menus/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Semana de carga"))
                .andExpect(jsonPath("$.tipo").value("SEMANAL"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                // Todos los días del rango, también el que no tiene comidas
                .andExpect(jsonPath("$.dias.length()").value(3))
                .andExpect(jsonPath("$.dias[0].fecha").value(LUNES.toString()))
                .andExpect(jsonPath("$.dias[2].comidas.length()").value(0))
                // Nutrientes por ración × raciones; la receta sin cálculo no suma
                .andExpect(jsonPath("$.dias[0].comidas[0].receta.nombre").value("Gachas de avena"))
                .andExpect(jsonPath("$.dias[0].comidas[0].nutrientes.kcal").value(400))
                .andExpect(jsonPath("$.dias[0].comidas[1].raciones").value(1.5))
                .andExpect(jsonPath("$.dias[0].comidas[1].nutrientes.kcal").value(900))
                .andExpect(jsonPath("$.dias[0].comidas[1].nutrientes.proteina").value(67.5))
                .andExpect(jsonPath("$.dias[0].comidas[2].nutrientes").doesNotExist())
                .andExpect(jsonPath("$.dias[0].comidas[2].bloqueada").value(false))
                .andExpect(jsonPath("$.dias[0].comidas[2].consumida").value(false))
                .andExpect(jsonPath("$.dias[0].planificado.kcal").value(1300))
                .andExpect(jsonPath("$.dias[1].planificado.kcal").value(1200))
                .andExpect(jsonPath("$.dias[2].planificado.kcal").value(0))
                // Día de entreno: hay un entrenamiento que cuenta (el omitido no)
                .andExpect(jsonPath("$.dias[0].diaEntreno").value(false))
                .andExpect(jsonPath("$.dias[1].diaEntreno").value(true))
                .andExpect(jsonPath("$.dias[2].diaEntreno").value(false))
                .andExpect(jsonPath("$.resumen.mediaKcal").value(833.3))
                .andExpect(jsonPath("$.resumen.proteinaMedia").value(59.2)));

        BigDecimal objetivo0 = numero(json, "$.dias[0].objetivo.total.kcal");
        BigDecimal objetivo1 = numero(json, "$.dias[1].objetivo.total.kcal");
        BigDecimal objetivo2 = numero(json, "$.dias[2].objetivo.total.kcal");
        assertThat(objetivo0).isPositive();
        // El entrenamiento sube el objetivo de su día
        assertThat(numero(json, "$.dias[1].objetivo.kcalEntreno")).isPositive();
        assertThat(objetivo1).isGreaterThan(objetivo0);
        assertThat(numero(json, "$.dias[0].porcentajeObjetivo"))
                .isEqualByComparingTo(porcentaje(new BigDecimal("1300"), objetivo0));
        assertThat(numero(json, "$.dias[1].porcentajeObjetivo"))
                .isEqualByComparingTo(porcentaje(new BigDecimal("1200"), objetivo1));
        assertThat(numero(json, "$.dias[2].porcentajeObjetivo")).isZero();
        assertThat(numero(json, "$.resumen.ajustePorcentaje"))
                .isEqualByComparingTo(porcentaje(new BigDecimal("2500"), objetivo0.add(objetivo1).add(objetivo2)));
    }

    @Test
    void sinPerfilLosDiasVanSinObjetivo() throws Exception {
        UUID id = menu(ana.usuario(), "Lunes", LUNES, LUNES, false, m -> m.anadirComida(
                new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE)));

        mockMvc.perform(conToken(get("/api/v1/menus/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("DIARIO"))
                .andExpect(jsonPath("$.dias[0].objetivo").doesNotExist())
                .andExpect(jsonPath("$.dias[0].porcentajeObjetivo").value(0))
                .andExpect(jsonPath("$.dias[0].planificado.kcal").value(400))
                .andExpect(jsonPath("$.resumen.ajustePorcentaje").doesNotExist())
                .andExpect(jsonPath("$.resumen.mediaKcal").value(400.0));
    }

    @Test
    void elListadoFiltraPorFavoritoYFechaYCuentaLasComidas() throws Exception {
        menu(ana.usuario(), "Pasado favorito", LUNES.minusDays(14), LUNES.minusDays(8), true, m -> { });
        menu(ana.usuario(), "Esta semana", LUNES, LUNES.plusDays(6), false, m -> {
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE));
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, BigDecimal.ONE));
        });
        menu(ana.usuario(), "Próxima favorito", LUNES.plusDays(7), LUNES.plusDays(13), true, m -> { });
        menu(crearUsuarioYToken().usuario(), "Ajeno", LUNES, LUNES.plusDays(6), true, m -> { });

        mockMvc.perform(conToken(get("/api/v1/menus"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].nombre")
                        .value(org.hamcrest.Matchers.contains("Próxima favorito", "Esta semana", "Pasado favorito")))
                .andExpect(jsonPath("$.content[1].numeroComidas").value(2))
                .andExpect(jsonPath("$.content[1].dias").doesNotExist());
        mockMvc.perform(conToken(get("/api/v1/menus").param("favorito", "true"), ana))
                .andExpect(jsonPath("$.content[*].nombre")
                        .value(org.hamcrest.Matchers.contains("Próxima favorito", "Pasado favorito")));
        mockMvc.perform(conToken(get("/api/v1/menus").param("favorito", "false").param("desde", LUNES.toString()), ana))
                .andExpect(jsonPath("$.content[*].nombre").value(org.hamcrest.Matchers.contains("Esta semana")));
        mockMvc.perform(conToken(get("/api/v1/menus").param("size", "51"), ana))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchCambiaSoloLosCamposPresentes() throws Exception {
        UUID id = menu(ana.usuario(), "Semana", LUNES, LUNES.plusDays(6), false, m -> { });

        mockMvc.perform(conToken(patch("/api/v1/menus/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"  Semana de carga  \", \"favorito\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Semana de carga"))
                .andExpect(jsonPath("$.favorito").value(true))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.dias.length()").value(7));
        mockMvc.perform(conToken(patch("/api/v1/menus/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ACTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Semana de carga"))
                .andExpect(jsonPath("$.favorito").value(true))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        MenuSemanal guardado = menuRepositorio.findById(id).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Semana de carga");
        assertThat(guardado.getEstado()).isEqualTo(EstadoMenu.ACTIVO);

        // Nombre en blanco, demasiado largo o estado desconocido: 400
        for (String cuerpo : List.of("{\"nombre\": \"   \"}", "{\"nombre\": \"" + "x".repeat(101) + "\"}",
                "{\"estado\": \"PAUSADO\"}")) {
            mockMvc.perform(conToken(patch("/api/v1/menus/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deleteBorraElMenuConSusComidas() throws Exception {
        UUID id = menu(ana.usuario(), "Lunes", LUNES, LUNES, false, m -> m.anadirComida(
                new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE)));

        mockMvc.perform(conToken(delete("/api/v1/menus/" + id), ana)).andExpect(status().isNoContent());

        assertThat(menuRepositorio.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comida_planificada", Long.class)).isZero();
        // La receta sigue
        assertThat(recetaRepositorio.existsById(avena.getId())).isTrue();
        mockMvc.perform(conToken(get("/api/v1/menus/" + id), ana)).andExpect(status().isNotFound());
    }

    @Test
    void losMenusDeOtroUsuarioNoExisten() throws Exception {
        UUID id = menu(ana.usuario(), "De Ana", LUNES, LUNES, false, m -> { });
        UsuarioConToken otro = crearUsuarioYToken();

        mockMvc.perform(conToken(get("/api/v1/menus/" + id), otro))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(conToken(patch("/api/v1/menus/" + id), otro).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorito\": true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete("/api/v1/menus/" + id), otro)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/api/v1/menus/" + UUID.randomUUID()), ana)).andExpect(status().isNotFound());

        MenuSemanal intacto = menuRepositorio.findById(id).orElseThrow();
        assertThat(intacto.isFavorito()).isFalse();
    }

    @Test
    void soloLosDeportistasYConConsentimiento() throws Exception {
        mockMvc.perform(get("/api/v1/menus")).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/api/v1/menus"), crearAdminYToken())).andExpect(status().isForbidden());

        mockMvc.perform(conToken(delete("/api/v1/cuenta/consentimiento-salud"), ana))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(conToken(get("/api/v1/menus"), ana))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REVOCADO"));
    }

    @Test
    void flujoCompletoDeUnMenuDeDosDias() throws Exception {
        // 2 días y 3 comidas, creados directamente con los repositorios
        UUID id = menu(ana.usuario(), "Dos días", LUNES, LUNES.plusDays(1), false, m -> {
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE));
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, new BigDecimal("1.5")));
            m.anadirComida(new ComidaPlanificada(LUNES.plusDays(1), TipoComida.CENA, pollo, new BigDecimal("2")));
        });
        String ruta = "/api/v1/menus/" + id;

        // GET: 2 días; totales = suma de (nutrientes por ración × raciones)
        //   día 1: avena ×1 (400 / 20 / 50 / 10) + pollo ×1.5 (900 / 67.5 / 75 / 15)
        //   día 2: pollo ×2 (1200 / 90 / 100 / 20)
        mockMvc.perform(conToken(get(ruta), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(2))
                .andExpect(jsonPath("$.dias[0].comidas.length()").value(2))
                .andExpect(jsonPath("$.dias[1].comidas.length()").value(1))
                .andExpect(jsonPath("$.dias[0].planificado.kcal").value(1300))
                .andExpect(jsonPath("$.dias[0].planificado.proteina").value(87.5))
                .andExpect(jsonPath("$.dias[0].planificado.carbohidratos").value(125))
                .andExpect(jsonPath("$.dias[0].planificado.grasa").value(25))
                .andExpect(jsonPath("$.dias[1].planificado.kcal").value(1200))
                .andExpect(jsonPath("$.dias[1].planificado.proteina").value(90))
                .andExpect(jsonPath("$.dias[1].planificado.carbohidratos").value(100))
                .andExpect(jsonPath("$.dias[1].planificado.grasa").value(20))
                .andExpect(jsonPath("$.favorito").value(false));

        // PATCH favorito → true
        mockMvc.perform(conToken(patch(ruta), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorito\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorito").value(true))
                .andExpect(jsonPath("$.nombre").value("Dos días"));

        // GET ?favorito=true lo devuelve
        mockMvc.perform(conToken(get("/api/v1/menus").param("favorito", "true"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].numeroComidas").value(3));

        // Otro usuario → 404 (y no puede borrarlo)
        UsuarioConToken otro = crearUsuarioYToken();
        mockMvc.perform(conToken(get(ruta), otro)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete(ruta), otro)).andExpect(status().isNotFound());

        // DELETE → 204, y después ya no existe
        mockMvc.perform(conToken(delete(ruta), ana)).andExpect(status().isNoContent());
        mockMvc.perform(conToken(get(ruta), ana)).andExpect(status().isNotFound());
        assertThat(menuRepositorio.existsById(id)).isFalse();
    }

    // ---------------------------------------------------------------------------------------------

    private UUID menu(Usuario usuario, String nombre, LocalDate inicio, LocalDate fin, boolean favorito,
            Consumer<MenuSemanal> configurar) {
        MenuSemanal menu = new MenuSemanal();
        menu.setUsuario(usuario);
        menu.setNombre(nombre);
        menu.setTipo(inicio.equals(fin) ? TipoMenu.DIARIO : TipoMenu.SEMANAL);
        menu.setFechaInicio(inicio);
        menu.setFechaFin(fin);
        menu.setFavorito(favorito);
        configurar.accept(menu);
        return menuRepositorio.saveAndFlush(menu).getId();
    }

    private Receta receta(String nombre, EstadoNutricional estado, String kcal, String proteina) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setEstadoNutricional(estado);
        if (kcal != null) {
            receta.setKcalRacion(new BigDecimal(kcal));
            receta.setProteinaRacion(new BigDecimal(proteina));
            receta.setCarbohidratosRacion(new BigDecimal("50"));
            receta.setGrasaRacion(new BigDecimal("10"));
        }
        return recetaRepositorio.saveAndFlush(receta);
    }

    private void crearPerfil(UsuarioConToken usuario) throws Exception {
        mockMvc.perform(conToken(put("/api/v1/perfil"), usuario).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""))
                .andExpect(status().isOk());
    }

    private void entrenar(UsuarioConToken usuario, LocalDate dia, String estado) throws Exception {
        String fechaHora = dia.atTime(18, 0).atZone(MADRID).toOffsetDateTime().toString();
        mockMvc.perform(conToken(post("/api/v1/entrenamientos"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoActividadCodigo": "CARRERA_12KMH", "fechaHora": "%s", "duracionMin": 60,
                                 "intensidad": "MEDIA", "estado": "%s"}""".formatted(fechaHora, estado)))
                .andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion,
            UsuarioConToken usuario) {
        return peticion.header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion());
    }

    private static String respuesta(org.springframework.test.web.servlet.ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static BigDecimal numero(String json, String ruta) {
        return new BigDecimal(JsonPath.read(json, ruta).toString());
    }

    private static BigDecimal porcentaje(BigDecimal planificado, BigDecimal objetivo) {
        return planificado.multiply(new BigDecimal("100")).divide(objetivo, 0, RoundingMode.HALF_UP);
    }
}
