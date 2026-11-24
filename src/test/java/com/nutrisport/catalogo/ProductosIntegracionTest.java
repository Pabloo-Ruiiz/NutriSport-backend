package com.nutrisport.catalogo;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Productos comerciales (§7.3, Ampliación) de punta a punta: {@code /api/v1/productos} y el registro de comidas
 * por código de barras, con PostgreSQL real y Open Food Facts simulado por un servidor HTTP local.
 */
@AutoConfigureMockMvc
class ProductosIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso OFF = new ServidorHttpFalso();

    private static final String NUTELLA = "3017624010701";
    private static final String INCOMPLETO = "8400000000031";
    private static final String HOY = LocalDate.now(ZoneId.of("Europe/Madrid")).toString();

    @DynamicPropertySource
    static void proveedorLocal(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.off.base-url", OFF::url);
        registro.add("app.integracion.off.search-url", OFF::url);
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;
    private final LimitadorProveedor limitador;
    private final EstadoProveedor estado;

    private UsuarioConToken deportista;

    @Autowired
    ProductosIntegracionTest(MockMvc mockMvc, JdbcTemplate jdbc, CacheManager cacheManager,
            LimitadorProveedor limitador, EstadoProveedor estado) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.cacheManager = cacheManager;
        this.limitador = limitador;
        this.estado = estado;
    }

    @BeforeEach
    void preparar() {
        limpiar();
        deportista = crearUsuarioYToken();
    }

    @AfterEach
    void limpiar() {
        OFF.reiniciar();
        cacheManager.getCache(CacheConfig.OFF_BUSQUEDA).clear();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // GET /productos/{codigoBarras}
    // ---------------------------------------------------------------------------------------------

    @Test
    void laPrimeraConsultaTraeElProductoYLoGuardaYLaSiguienteSaleDelCatalogo() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));

        String id = JsonPath.read(producto(NUTELLA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoBarras").value(NUTELLA))
                .andExpect(jsonPath("$.nombre").value("Nutella"))
                .andExpect(jsonPath("$.unidadBase").value("G"))
                .andExpect(jsonPath("$.por100.kcal").value(539.0))
                .andExpect(jsonPath("$.por100.fibra").doesNotExist())
                .andExpect(jsonPath("$.alergenosContiene[0]").value("FRUTOS_DE_CASCARA"))
                .andExpect(jsonPath("$.alergenosRevisados").value(false))
                .andExpect(jsonPath("$.nutricionCompleta").value(true))
                .andExpect(jsonPath("$.fuente.proveedor").value("OPEN_FOOD_FACTS"))
                .andExpect(jsonPath("$.fuente.licencia").value("ODbL"))
                .andExpect(jsonPath("$.fuente.url").value("https://world.openfoodfacts.org/product/" + NUTELLA))
                .andExpect(jsonPath("$.fuente.atribucion").value("Datos de Open Food Facts (ODbL) · openfoodfacts.org"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(id).isNotNull();

        producto(NUTELLA).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(OFF.recibidas()).hasSize(1);
    }

    @Test
    void unaCopiaDeMasDe30DiasSeRefrescaConElProveedor() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));
        producto(NUTELLA).andExpect(status().isOk());
        envejecerCopia(NUTELLA);
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json").replace("539", "540")));

        producto(NUTELLA).andExpect(status().isOk()).andExpect(jsonPath("$.por100.kcal").value(540.0));

        assertThat(OFF.recibidas()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alimento", Integer.class)).isEqualTo(1);
    }

    @Test
    void conElProveedorCaidoSeDevuelveLaCopiaLocalAunqueSeaAntigua() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));
        String id = JsonPath.read(producto(NUTELLA).andReturn().getResponse().getContentAsString(), "$.id");
        envejecerCopia(NUTELLA);
        OFF.encolar(Respuesta.estado(503, Map.of("Retry-After", "120")));

        producto(NUTELLA).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void sinCopiaLocalYConElProveedorCaidoResponde503ConRetryAfter() throws Exception {
        OFF.encolar(Respuesta.estado(503, Map.of("Retry-After", "120")));

        producto(NUTELLA)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_NO_DISPONIBLE"));
    }

    @Test
    void unProductoQueNoExisteEnNingunSitioResponde404() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-no-encontrado.json")));

        producto("0000000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void unProductoConLaNutricionIncompletaSeMuestraSinIdYNoSeGuarda() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto-incompleto.json")));

        producto(INCOMPLETO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.nutricionCompleta").value(false))
                .andExpect(jsonPath("$.por100.kcal").value(480))
                .andExpect(jsonPath("$.por100.proteinas").doesNotExist())
                .andExpect(jsonPath("$.alergenosContiene[0]").value("LACTEOS"))
                .andExpect(jsonPath("$.fuente.atribucion").exists());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alimento", Integer.class)).isZero();
    }

    @Test
    void unCodigoNoValidoResponde400SinLlamarAlProveedor() throws Exception {
        producto("1234567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("codigoBarras"));
        producto("12345678901234x").andExpect(status().isBadRequest());

        assertThat(OFF.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // GET /productos?q=
    // ---------------------------------------------------------------------------------------------

    @Test
    void laBusquedaNoGuardaNadaYMarcaLosQueYaEstanEnElCatalogo() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));
        String id = JsonPath.read(producto(NUTELLA).andReturn().getResponse().getContentAsString(), "$.id");
        OFF.encolar(Respuesta.json(recurso("integracion/off-busqueda.json")));

        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "nutella").param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Proveedor-No-Disponible"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].nutricionCompleta").value(true))
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].marca").value("Marca Ficticia, Otra Marca"))
                .andExpect(jsonPath("$[1].nutricionCompleta").value(false))
                .andExpect(jsonPath("$[1].fuente.licencia").value("ODbL"));

        // La página 1 de la API (desde 0) es la 2 de Search-a-licious (desde 1)
        assertThat(OFF.recibidas().get(1).parametro("page")).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alimento", Integer.class)).isEqualTo(1);
    }

    @Test
    void conElProveedorCaidoLaBusquedaDevuelveUnaListaVaciaYLaCabecera() throws Exception {
        OFF.encolar(Respuesta.estado(429));

        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "nutella")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Proveedor-No-Disponible", "OPEN_FOOD_FACTS"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elFrontendPuedeLeerLaCabeceraDeProveedorNoDisponibleYRetryAfter() throws Exception {
        OFF.encolar(Respuesta.estado(429));

        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "nutella"))
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("X-Proveedor-No-Disponible"),
                                org.hamcrest.Matchers.containsString("Retry-After"))));
    }

    @Test
    void parametrosDeBusquedaNoValidosResponden400() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "a"))).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "nutella").param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("page"));
        mockMvc.perform(conToken(get("/api/v1/productos").param("q", "nutella").param("page", "50")))
                .andExpect(status().isBadRequest());

        assertThat(OFF.recibidas()).isEmpty();
    }

    @Test
    void unAdministradorSinRolDeportistaNoPuedeUsarlos() throws Exception {
        UsuarioConToken admin = crearAdminYToken();

        mockMvc.perform(get("/api/v1/productos/" + NUTELLA).header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------
    // POST /comidas con codigoBarras
    // ---------------------------------------------------------------------------------------------

    @Test
    void unaComidaConTodasLasLineasPorCodigoTieneOrigenCodigoBarras() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));

        // El mismo código dos veces: una sola consulta al proveedor
        registrarComida("""
                [{"codigoBarras": "%s", "cantidad": 15, "unidad": "g"},
                 {"codigoBarras": "%s", "cantidad": 10, "unidad": "g"}]""".formatted(NUTELLA, NUTELLA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("CODIGO_BARRAS"))
                .andExpect(jsonPath("$.alimentos.length()").value(2))
                .andExpect(jsonPath("$.alimentos[0].nombre").value("Nutella"))
                // 539 kcal/100 g × 25 g
                .andExpect(jsonPath("$.totales.kcal").value(134.8));

        assertThat(OFF.recibidas()).hasSize(1);
    }

    @Test
    void unaComidaMixtaConservaElOrigenManual() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto.json")));
        String id = JsonPath.read(producto(NUTELLA).andReturn().getResponse().getContentAsString(), "$.id");

        registrarComida("""
                [{"codigoBarras": "%s", "cantidad": 15, "unidad": "g"},
                 {"alimentoId": "%s", "cantidad": 10, "unidad": "g"}]""".formatted(NUTELLA, id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("MANUAL"));
    }

    @Test
    void unProductoConLaNutricionIncompletaNoSePuedeRegistrar() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-producto-incompleto.json")));

        registrarComida("""
                [{"codigoBarras": "%s", "cantidad": 30, "unidad": "g"}]""".formatted(INCOMPLETO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("NUTRICION_INCOMPLETA"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM registro_comida", Integer.class)).isZero();
    }

    @Test
    void unCodigoDesconocidoAlRegistrarResponde404() throws Exception {
        OFF.encolar(Respuesta.json(recurso("integracion/off-no-encontrado.json")));

        registrarComida("""
                [{"codigoBarras": "0000000000000", "cantidad": 30, "unidad": "g"}]""")
                .andExpect(status().isNotFound());
    }

    @Test
    void cadaLineaLlevaAlimentoIdOCodigoBarrasPeroNoLosDos() throws Exception {
        registrarComida("""
                [{"alimentoId": "3f8b2c1e-6a4d-4e2b-9c7a-1d2e3f4a5b6c", "codigoBarras": "%s",
                  "cantidad": 30, "unidad": "g"}]""".formatted(NUTELLA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("alimentos[0].referenciaValida"));
        registrarComida("""
                [{"cantidad": 30, "unidad": "g"}]""")
                .andExpect(status().isBadRequest());

        assertThat(OFF.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    private ResultActions producto(String codigo) throws Exception {
        return mockMvc.perform(conToken(get("/api/v1/productos/" + codigo)));
    }

    private ResultActions registrarComida(String alimentos) throws Exception {
        return mockMvc.perform(conToken(post("/api/v1/comidas")).contentType(MediaType.APPLICATION_JSON).content("""
                {"fecha": "%s", "tipoComida": "MERIENDA", "alimentos": %s}""".formatted(HOY, alimentos)));
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", deportista.cabeceraAutorizacion());
    }

    private void envejecerCopia(String codigo) {
        jdbc.update("UPDATE alimento_fuente SET sincronizado_en = now() - interval '31 days' WHERE referencia_externa = ?",
                codigo);
    }
}
