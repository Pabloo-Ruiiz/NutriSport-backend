package com.nutrisport.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.nutrisport.comidas.entidad.TipoComida;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
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
 * Edición de menús (§3.D y §7.5) contra PostgreSQL real: alternativas, bloqueo y regeneración, registro de comidas
 * planificadas, duplicado, añadir comidas y las pendientes del resumen diario.
 * <p>
 * Todas las recetas tienen un único ingrediente, el mismo alimento (200 kcal, 10 g de proteína, 25 g de HC y 8 g de
 * grasa por 100 g), y 2 raciones: con {@code kcal} gramos en total, cada ración aporta exactamente kcal, kcal/20 de
 * proteína, kcal/8 de HC y kcal/25 de grasa. Así los nutrientes por ración coinciden con los de sus ingredientes y
 * todas tienen el mismo perfil de macros (las raciones que mejor ajustan dependen solo de la energía).
 */
@AutoConfigureMockMvc
class EdicionMenuTest extends IntegracionBase {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.now(MADRID);
    private static final LocalDate LUNES = HOY.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");
    private static final String REFERENCIA_ALIMENTO = "TEST-EDICION-MENU";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private Alimento base;

    private Receta desayuno;
    private Receta tostadaGrande;
    private Receta original;
    private Receta cena;
    private Receta alternativa680;
    private Receta alternativa720;
    private Receta alternativa600;
    private Receta sinVerificar;
    private Receta ligera;
    private Receta incompleta;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        ana = crearUsuarioYToken();
        crearPerfil();
        base = alimentoBase();
        desayuno = receta("Gachas de avena", List.of("DESAYUNO"), 500);
        // Solo de desayuno: nunca es alternativa de una comida
        tostadaGrande = receta("Tostada grande", List.of("DESAYUNO"), 700);
        original = receta("Pollo con arroz", List.of("COMIDA", "CENA"), 700);
        cena = receta("Merluza con verduras", List.of("COMIDA", "CENA"), 650);
        alternativa680 = receta("Lentejas estofadas", List.of("COMIDA", "CENA"), 680);
        alternativa720 = receta("Salmón con patata", List.of("COMIDA", "CENA"), 720);
        // Con 1,25 raciones: 750 kcal (+7,1 %)
        alternativa600 = receta("Tortilla con ensalada", List.of("COMIDA", "CENA"), 600);
        sinVerificar = receta("Pasta con atún", List.of("COMIDA", "CENA"), 700);
        sinVerificar.setAlergenosVerificados(false);
        sinVerificar = recetaRepositorio.saveAndFlush(sinVerificar);
        // Ni con 2 raciones (300 kcal) llega al −15 %
        ligera = receta("Caldo de verduras", List.of("COMIDA", "CENA"), 150);
        incompleta = receta("Guiso sin calcular", List.of("COMIDA", "CENA"), 700);
        incompleta.setEstadoNutricional(EstadoNutricional.INCOMPLETO);
        incompleta = recetaRepositorio.saveAndFlush(incompleta);
        // Alergia estricta al gluten: las recetas sin alérgenos verificados no son compatibles
        mockMvc.perform(con(put("/api/v1/perfil/preferencias")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"preferencias": [{"tipo": "ALERGIA", "alergeno": "GLUTEN", "severidad": "ESTRICTA"}]}"""))
                .andExpect(status().isOk());
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE menu_semanal, receta, registro_comida, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa = ?", REFERENCIA_ALIMENTO);
    }

    @Test
    void lasAlternativasSonTresRecetasCompatiblesConLaEnergiaDentroDel15PorCiento() throws Exception {
        MenuSemanal menu = menuDeHoy(EstadoMenu.ACTIVO);
        UUID comidaId = comida(menu, TipoComida.COMIDA);

        String json = cuerpo(mockMvc.perform(con(get(ruta(menu) + "/comidas/" + comidaId + "/alternativas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)));

        // Ni la actual ni la del mismo día, ni la incompatible, ni la de otra franja, ni la que no llega al −15 %
        List<String> ids = JsonPath.read(json, "$[*].receta.id");
        assertThat(ids).containsExactlyInAnyOrder(alternativa680.getId().toString(),
                alternativa720.getId().toString(), alternativa600.getId().toString());
        List<Boolean> compatibles = JsonPath.read(json, "$[*].receta.compatible");
        assertThat(compatibles).containsOnly(true);
        List<Object> diferencias = JsonPath.read(json, "$[*].diferenciaKcalPct");
        assertThat(diferencias).allSatisfy(diferencia -> assertThat(new BigDecimal(diferencia.toString()).abs())
                .isLessThanOrEqualTo(new BigDecimal("15")));
        List<Object> kcal = JsonPath.read(json, "$[*].nutrientes.kcal");
        assertThat(kcal).allSatisfy(valor -> assertThat(new BigDecimal(valor.toString()))
                .isBetween(new BigDecimal("595"), new BigDecimal("805")));

        // Las raciones son las que mejor ajustan a la original: 1,25 para la de 600 kcal
        Map<String, Object> deLa600 = JsonPath.<List<Map<String, Object>>>read(json,
                "$[?(@.receta.id == '" + alternativa600.getId() + "')]").get(0);
        assertThat(new BigDecimal(deLa600.get("raciones").toString())).isEqualByComparingTo("1.25");
        assertThat(new BigDecimal(deLa600.get("diferenciaKcalPct").toString())).isEqualByComparingTo("7.1");
    }

    @Test
    void regenerarRespetaLasComidasBloqueadasYLasConsumidasYCambiaElResto() throws Exception {
        MenuSemanal menu = menuDeHoy(EstadoMenu.ACTIVO);
        UUID desayunoId = comida(menu, TipoComida.DESAYUNO);
        UUID comidaId = comida(menu, TipoComida.COMIDA);
        UUID cenaId = comida(menu, TipoComida.CENA);

        // Bloquear la comida con unas raciones que el motor nunca propone, y registrar el desayuno
        mockMvc.perform(con(put(ruta(menu) + "/comidas/" + comidaId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bloqueada\": true, \"raciones\": 1.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].comidas[?(@.id == '" + comidaId + "')].bloqueada").value(true));
        mockMvc.perform(con(post(ruta(menu) + "/comidas/" + desayunoId + "/registrar")))
                .andExpect(status().isCreated());

        String json = cuerpo(mockMvc.perform(con(post(ruta(menu) + "/regenerar")))
                .andExpect(status().isOk()));

        List<String> franjas = JsonPath.read(json, "$.dias[0].comidas[*].tipoComida");
        assertThat(franjas).containsExactly("DESAYUNO", "COMIDA", "CENA");
        List<String> ids = JsonPath.read(json, "$.dias[0].comidas[*].id");
        // La bloqueada y la consumida siguen igual; la cena es una comida nueva elegida por el motor
        assertThat(ids).contains(desayunoId.toString(), comidaId.toString()).doesNotContain(cenaId.toString());
        Map<String, Object> bloqueada = unica(json, comidaId);
        assertThat(JsonPath.<String>read(bloqueada, "$.receta.id")).isEqualTo(original.getId().toString());
        assertThat(new BigDecimal(bloqueada.get("raciones").toString())).isEqualByComparingTo("1.3");
        assertThat(bloqueada.get("bloqueada")).isEqualTo(true);
        Map<String, Object> consumida = unica(json, desayunoId);
        assertThat(JsonPath.<String>read(consumida, "$.receta.id")).isEqualTo(desayuno.getId().toString());
        assertThat(consumida.get("consumida")).isEqualTo(true);
        // La receta bloqueada no se repite en la cena
        String recetaCena = JsonPath.<List<String>>read(json,
                "$.dias[0].comidas[?(@.tipoComida == 'CENA')].receta.id").get(0);
        assertThat(recetaCena).isNotEqualTo(original.getId().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comida_planificada", Long.class)).isEqualTo(3);
    }

    @Test
    void registrarCreaUnaComidaDeOrigenPlanConLaRecetaPorLasRacionesYNoSePuedeRepetir() throws Exception {
        MenuSemanal menu = menuDeHoy(EstadoMenu.ACTIVO);
        UUID comidaId = comida(menu, TipoComida.COMIDA);
        mockMvc.perform(con(put(ruta(menu) + "/comidas/" + comidaId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raciones\": 1.5}"))
                .andExpect(status().isOk());

        // 700 kcal, 35 g de proteína, 87,5 g de HC y 28 g de grasa por ración × 1,5
        String json = cuerpo(mockMvc.perform(con(post(ruta(menu) + "/comidas/" + comidaId + "/registrar"))
                        .param("hora", "14:30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("PLAN"))
                .andExpect(jsonPath("$.fecha").value(HOY.toString()))
                .andExpect(jsonPath("$.hora").value("14:30:00"))
                .andExpect(jsonPath("$.tipoComida").value("COMIDA"))
                .andExpect(jsonPath("$.alimentos.length()").value(1))
                // 700 g de la receta para 2 raciones × 1,5 / 2
                .andExpect(jsonPath("$.alimentos[0].gramos").value(525.0))
                .andExpect(jsonPath("$.totales.kcal").value(1050.0))
                .andExpect(jsonPath("$.totales.proteina").value(52.5))
                // 131,25 redondeado a un decimal
                .andExpect(jsonPath("$.totales.carbohidratos").value(closeTo(131.25, 0.1)))
                .andExpect(jsonPath("$.totales.grasa").value(42.0)));
        String registroId = JsonPath.read(json, "$.id");

        // La comida planificada queda consumida y enlazada con el registro
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT consumida, registro_comida_id FROM comida_planificada WHERE id = ?", comidaId);
        assertThat(fila.get("consumida")).isEqualTo(true);
        assertThat(fila.get("registro_comida_id").toString()).isEqualTo(registroId);
        assertThat(jdbc.queryForObject("SELECT origen FROM registro_comida WHERE id = ?", String.class,
                UUID.fromString(registroId))).isEqualTo("PLAN");

        // Dos veces no: 409 y no se crea otro registro; tampoco se puede cambiar
        mockMvc.perform(con(post(ruta(menu) + "/comidas/" + comidaId + "/registrar")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COMIDA_CONSUMIDA"));
        mockMvc.perform(con(put(ruta(menu) + "/comidas/" + comidaId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raciones\": 2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("COMIDA_CONSUMIDA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registro_comida", Long.class)).isEqualTo(1);
    }

    @Test
    void duplicarALaSemanaSiguienteDesplazaLosSieteDiasSinConsumoNiFavorito() throws Exception {
        List<Receta> recetas = List.of(original, cena, alternativa680, alternativa720, alternativa600, original, cena);
        MenuSemanal semana = new MenuSemanal();
        semana.setUsuario(ana.usuario());
        semana.setNombre("Semana de carga");
        semana.setTipo(TipoMenu.SEMANAL);
        semana.setFechaInicio(LUNES);
        semana.setFechaFin(LUNES.plusDays(6));
        semana.setFavorito(true);
        semana.setEstado(EstadoMenu.ACTIVO);
        for (int dia = 0; dia < 7; dia++) {
            ComidaPlanificada comida = new ComidaPlanificada(LUNES.plusDays(dia), TipoComida.COMIDA, recetas.get(dia),
                    new BigDecimal("1.25"));
            comida.setBloqueada(dia == 2);
            comida.setConsumida(dia == 0);
            semana.anadirComida(comida);
        }
        semana = menuRepositorio.saveAndFlush(semana);
        LocalDate siguiente = LUNES.plusWeeks(1);

        String json = cuerpo(mockMvc.perform(con(post(ruta(semana) + "/duplicar"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\": \"" + siguiente + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SEMANAL"))
                .andExpect(jsonPath("$.nombre").value("Copia de Semana de carga"))
                .andExpect(jsonPath("$.fechaInicio").value(siguiente.toString()))
                .andExpect(jsonPath("$.fechaFin").value(siguiente.plusDays(6).toString()))
                .andExpect(jsonPath("$.favorito").value(false))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.dias.length()").value(7)));
        assertThat(JsonPath.<String>read(json, "$.id")).isNotEqualTo(semana.getId().toString());

        for (int dia = 0; dia < 7; dia++) {
            String prefijo = "$.dias[" + dia + "]";
            assertThat(JsonPath.<String>read(json, prefijo + ".fecha")).isEqualTo(siguiente.plusDays(dia).toString());
            assertThat(JsonPath.<List<Object>>read(json, prefijo + ".comidas")).hasSize(1);
            assertThat(JsonPath.<String>read(json, prefijo + ".comidas[0].receta.id"))
                    .isEqualTo(recetas.get(dia).getId().toString());
            assertThat(JsonPath.<String>read(json, prefijo + ".comidas[0].fecha"))
                    .isEqualTo(siguiente.plusDays(dia).toString());
            assertThat(new BigDecimal(JsonPath.read(json, prefijo + ".comidas[0].raciones").toString()))
                    .isEqualByComparingTo("1.25");
            // Sin consumo; el bloqueo sí se conserva
            assertThat(JsonPath.<Boolean>read(json, prefijo + ".comidas[0].consumida")).isFalse();
            assertThat(JsonPath.<Boolean>read(json, prefijo + ".comidas[0].bloqueada")).isEqualTo(dia == 2);
        }

        // El original no cambia
        mockMvc.perform(con(get(ruta(semana))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicio").value(LUNES.toString()))
                .andExpect(jsonPath("$.favorito").value(true))
                .andExpect(jsonPath("$.dias[0].comidas[0].consumida").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comida_planificada", Long.class)).isEqualTo(14);

        // Una fecha pasada no vale
        mockMvc.perform(con(post(ruta(semana) + "/duplicar")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\": \"" + HOY.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fechaInicio"));
    }

    @Test
    void elResumenDiarioMuestraLasComidasPlanificadasPendientesDelDia() throws Exception {
        MenuSemanal menu = menuDeHoy(EstadoMenu.ACTIVO);
        UUID desayunoId = comida(menu, TipoComida.DESAYUNO);
        UUID comidaId = comida(menu, TipoComida.COMIDA);
        UUID cenaId = comida(menu, TipoComida.CENA);
        // Ni los menús en borrador ni las comidas de otros días cuentan
        menuDeHoy(EstadoMenu.BORRADOR);
        MenuSemanal manana = menuDeHoy(EstadoMenu.ACTIVO);
        manana.setFechaInicio(HOY.plusDays(1));
        manana.setFechaFin(HOY.plusDays(1));
        manana.getComidas().forEach(comida -> comida.setFecha(HOY.plusDays(1)));
        menuRepositorio.saveAndFlush(manana);

        mockMvc.perform(con(post(ruta(menu) + "/comidas/" + desayunoId + "/registrar")))
                .andExpect(status().isCreated());

        String json = cuerpo(mockMvc.perform(con(get("/api/v1/seguimiento/resumen")).param("fecha", HOY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comidas.length()").value(1))
                .andExpect(jsonPath("$.comidasPlanificadasPendientes.length()").value(2)));
        assertThat(JsonPath.<List<String>>read(json, "$.comidasPlanificadasPendientes[*].id"))
                .containsExactly(comidaId.toString(), cenaId.toString());
        assertThat(JsonPath.<List<String>>read(json, "$.comidasPlanificadasPendientes[*].menuId"))
                .containsOnly(menu.getId().toString());
        assertThat(JsonPath.<List<String>>read(json, "$.comidasPlanificadasPendientes[*].receta.nombre"))
                .containsExactly(original.getNombre(), cena.getNombre());
        // 700 kcal por ración × 1
        assertThat(JsonPath.<Double>read(json, "$.comidasPlanificadasPendientes[0].nutrientes.kcal"))
                .isEqualTo(700.0);
    }

    @Test
    void anadirUnaRecetaExigeCalculoCompatibilidadYUnaFechaDelMenu() throws Exception {
        MenuSemanal menu = menuDeHoy(EstadoMenu.ACTIVO);

        anadir(menu, incompleta, HOY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("NUTRICION_INCOMPLETA"));
        anadir(menu, sinVerificar, HOY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RECETA_INCOMPATIBLE"));
        anadir(menu, ligera, HOY.plusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fecha"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comida_planificada", Long.class)).isEqualTo(3);

        // Una receta de otra franja se puede añadir a mano
        anadir(menu, tostadaGrande, HOY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].comidas.length()").value(4))
                .andExpect(jsonPath("$.dias[0].comidas[3].receta.nombre").value("Tostada grande"))
                .andExpect(jsonPath("$.dias[0].comidas[3].tipoComida").value("MERIENDA"));
    }

    // ---------------------------------------------------------------------------------------------

    /** Menú diario de hoy: desayuno, comida (la receta original, 1 ración) y cena. */
    private MenuSemanal menuDeHoy(EstadoMenu estado) {
        MenuSemanal menu = new MenuSemanal();
        menu.setUsuario(ana.usuario());
        menu.setNombre("Hoy");
        menu.setTipo(TipoMenu.DIARIO);
        menu.setFechaInicio(HOY);
        menu.setFechaFin(HOY);
        menu.setEstado(estado);
        menu.anadirComida(new ComidaPlanificada(HOY, TipoComida.DESAYUNO, desayuno, BigDecimal.ONE));
        menu.anadirComida(new ComidaPlanificada(HOY, TipoComida.COMIDA, original, BigDecimal.ONE));
        menu.anadirComida(new ComidaPlanificada(HOY, TipoComida.CENA, cena, BigDecimal.ONE));
        return menuRepositorio.saveAndFlush(menu);
    }

    private static UUID comida(MenuSemanal menu, TipoComida tipo) {
        return menu.getComidas().stream()
                .filter(comida -> comida.getTipoComida() == tipo)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static String ruta(MenuSemanal menu) {
        return "/api/v1/menus/" + menu.getId();
    }

    private static Map<String, Object> unica(String json, UUID comidaId) {
        List<Map<String, Object>> encontradas = JsonPath.read(json,
                "$.dias[*].comidas[?(@.id == '" + comidaId + "')]");
        assertThat(encontradas).hasSize(1);
        return encontradas.get(0);
    }

    private ResultActions anadir(MenuSemanal menu, Receta receta, LocalDate fecha) throws Exception {
        String tipo = receta == tostadaGrande ? "MERIENDA" : "CENA";
        return mockMvc.perform(con(post(ruta(menu) + "/comidas")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recetaId\": \"" + receta.getId() + "\", \"fecha\": \"" + fecha
                        + "\", \"tipoComida\": \"" + tipo + "\", \"raciones\": 1}"));
    }

    private Alimento alimentoBase() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Mezcla de prueba");
        alimento.setCategoria(categoriaRepositorio.getReferenceById(CATEGORIA_OTROS));
        alimento.setKcal100(new BigDecimal("200"));
        alimento.setProteinas100(new BigDecimal("10"));
        alimento.setCarbohidratos100(new BigDecimal("25"));
        alimento.setGrasas100(new BigDecimal("8"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAlergenosRevisados(true);
        alimento.setAptoVegetariano(true);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setFuente(FuenteDato.BEDCA);
        alimento.setReferenciaExterna(REFERENCIA_ALIMENTO);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    /** Receta publicada de 2 raciones con {@code kcal} gramos del alimento base: {@code kcal} kcal por ración. */
    private Receta receta(String nombre, List<String> franjas, int kcal) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setRaciones((short) 2);
        receta.setRacionesSupuestas(false);
        receta.setFranjasAptas(new ArrayList<>(franjas));
        RecetaIngrediente ingrediente = new RecetaIngrediente(base.getNombre(), kcal + " g");
        ingrediente.setAlimento(base);
        ingrediente.setCantidadGramos(BigDecimal.valueOf(kcal));
        ingrediente.setEstadoConversion(EstadoConversion.EXACTA);
        receta.anadirIngrediente(ingrediente);
        BigDecimal energia = BigDecimal.valueOf(kcal);
        receta.setEstadoNutricional(EstadoNutricional.COMPLETO);
        receta.setCompletitudPct((short) 100);
        receta.setKcalRacion(energia);
        receta.setProteinaRacion(energia.divide(new BigDecimal("20")));
        receta.setCarbohidratosRacion(energia.divide(new BigDecimal("8")));
        receta.setGrasaRacion(energia.divide(new BigDecimal("25")));
        receta.setAlergenosVerificados(true);
        receta.setAptoVegetariano(true);
        return recetaRepositorio.saveAndFlush(receta);
    }

    private void crearPerfil() throws Exception {
        mockMvc.perform(con(put("/api/v1/perfil")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 3}"""))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, ana.cabeceraAutorizacion());
    }

    private static String cuerpo(ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
