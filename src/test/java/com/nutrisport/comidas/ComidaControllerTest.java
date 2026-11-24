package com.nutrisport.comidas;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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

/**
 * Registro de comidas (§7.4): totales, instantánea nutricional, día, duplicado, aislamiento entre
 * usuarios y avisos de restricciones.
 */
@AutoConfigureMockMvc
class ComidaControllerTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/comidas";
    private static final UUID CATEGORIA_CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");

    private final MockMvc mockMvc;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JdbcTemplate jdbc;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));

    private UsuarioConToken usuario;
    private UsuarioConToken otroUsuario;
    private Alimento arroz;
    private Alimento pollo;

    @Autowired
    ComidaControllerTest(MockMvc mockMvc, AlimentoRepositorio alimentoRepositorio,
            CategoriaRepositorio categoriaRepositorio, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        usuario = crearUsuarioYToken();
        otroUsuario = crearUsuarioYToken();
        arroz = guardarAlimento("Arroz blanco cocido", "130", "2.7", "28.0", "0.3", List.of(), List.of(), false);
        pollo = guardarAlimento("Pechuga de pollo a la plancha", "165", "31.0", "0.0", "3.6", List.of(), List.of(),
                false);
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    // ---------------------------------------------------------------------------------------------
    // Registro, instantánea y consultas
    // ---------------------------------------------------------------------------------------------

    @Test
    void crearUnaComidaConDosAlimentosDevuelve201ConLosTotales() throws Exception {
        // Arroz 200 g: 260 kcal · 5,4 P · 56 HC · 0,6 G. Pollo 150 g: 247,5 kcal · 46,5 P · 0 HC · 5,4 G
        crear(usuario, comidaArrozYPollo(hoy, "COMIDA"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.alimentos.length()").value(2))
                .andExpect(jsonPath("$.alimentos[0].gramos").value(200.0))
                .andExpect(jsonPath("$.alimentos[0].kcal").value(260.0))
                .andExpect(jsonPath("$.alimentos[1].kcal").value(247.5))
                .andExpect(jsonPath("$.totales.kcal").value(507.5))
                .andExpect(jsonPath("$.totales.proteina").value(51.9))
                .andExpect(jsonPath("$.totales.carbohidratos").value(56.0))
                .andExpect(jsonPath("$.totales.grasa").value(6.0))
                .andExpect(jsonPath("$.avisos").isEmpty());
    }

    @Test
    void cambiarElAlimentoDelCatalogoNoCambiaLaComidaYaRegistrada() throws Exception {
        String id = idDe(crear(usuario, comidaArrozYPollo(hoy, "COMIDA")));

        jdbc.update("UPDATE alimento SET kcal100 = 999 WHERE id = ?", arroz.getId());

        mockMvc.perform(conToken(get(RUTA + "/{id}", id), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alimentos[0].kcal").value(260.0))
                .andExpect(jsonPath("$.totales.kcal").value(507.5));
    }

    @Test
    void elDiaDevuelveSusComidasYLaSumaDeTotales() throws Exception {
        crear(usuario, comida(hoy, "08:00", "DESAYUNO", arroz.getId(), "100", "g")).andExpect(status().isCreated());
        crear(usuario, comida(hoy, "14:00", "COMIDA", pollo.getId(), "100", "g")).andExpect(status().isCreated());
        crear(usuario, comida(hoy.minusDays(1), "14:00", "COMIDA", pollo.getId(), "100", "g"))
                .andExpect(status().isCreated());

        // 130 + 165 kcal; la comida de ayer no cuenta
        mockMvc.perform(conToken(get(RUTA + "/dia/{fecha}", hoy), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.comidas.length()").value(2))
                .andExpect(jsonPath("$.comidas[0].tipoComida").value("DESAYUNO"))
                .andExpect(jsonPath("$.comidas[1].tipoComida").value("COMIDA"))
                .andExpect(jsonPath("$.totales.kcal").value(295.0));
    }

    @Test
    void duplicarCreaUnaComidaNuevaConOrigenCopia() throws Exception {
        String id = idDe(crear(usuario, comidaArrozYPollo(hoy, "COMIDA")));

        mockMvc.perform(conToken(post(RUTA + "/{id}/duplicar", id), usuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\": \"%s\", \"tipoComida\": \"CENA\"}".formatted(hoy.minusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(not(id)))
                .andExpect(jsonPath("$.origen").value("COPIA"))
                .andExpect(jsonPath("$.fecha").value(hoy.minusDays(1).toString()))
                .andExpect(jsonPath("$.tipoComida").value("CENA"))
                .andExpect(jsonPath("$.totales.kcal").value(507.5));
    }

    @Test
    void laComidaDeOtroUsuarioDevuelve404AlConsultarlaEditarlaOBorrarla() throws Exception {
        String id = idDe(crear(usuario, comidaArrozYPollo(hoy, "COMIDA")));

        mockMvc.perform(conToken(get(RUTA + "/{id}", id), otroUsuario))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(conToken(put(RUTA + "/{id}", id), otroUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comidaArrozYPollo(hoy, "CENA")))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete(RUTA + "/{id}", id), otroUsuario))
                .andExpect(status().isNotFound());

        // La comida sigue intacta para su dueño
        mockMvc.perform(conToken(get(RUTA + "/{id}", id), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoComida").value("COMIDA"));
    }

    // ---------------------------------------------------------------------------------------------
    // Avisos de restricciones (informan, no bloquean)
    // ---------------------------------------------------------------------------------------------

    @Test
    void unAlimentoConGlutenYAlergiaEstrictaSeRegistraConUnAviso() throws Exception {
        Alimento pan = guardarAlimento("Pan blanco", "260", "9.0", "51.0", "1.6", List.of("GLUTEN"), List.of(), false);
        fijarPreferencias(usuario, "{\"tipo\": \"ALERGIA\", \"alergeno\": \"GLUTEN\", \"severidad\": \"ESTRICTA\"}");

        crear(usuario, comida(hoy, "08:00", "DESAYUNO", pan.getId(), "60", "g"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]").value("Pan blanco: Contiene GLUTEN"));
    }

    @Test
    void unAlimentoConTrazasDeGlutenGeneraElAvisoDeTrazas() throws Exception {
        Alimento galletas = guardarAlimento("Galletas de avena sin gluten", "450", "7.0", "65.0", "17.0", List.of(),
                List.of("GLUTEN"), false);
        fijarPreferencias(usuario, "{\"tipo\": \"ALERGIA\", \"alergeno\": \"GLUTEN\", \"severidad\": \"ESTRICTA\"}");

        crear(usuario, comida(hoy, "17:00", "MERIENDA", galletas.getId(), "30", "g"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos.length()").value(1))
                .andExpect(jsonPath("$.avisos[0]").value("Galletas de avena sin gluten: Puede contener trazas de GLUTEN"));
    }

    @Test
    void unYogurSinLactosaNoAvisaAUnIntoleranteALaLactosa() throws Exception {
        // Contiene el alérgeno LACTEOS pero no lactosa: la intolerancia no es una alergia a la leche
        Alimento yogur = guardarAlimento("Yogur natural sin lactosa", "61", "3.8", "4.6", "3.0", List.of("LACTEOS"),
                List.of(), false);
        fijarPreferencias(usuario, "{\"tipo\": \"INTOLERANCIA\", \"intolerancia\": \"LACTOSA\", \"severidad\": \"ESTRICTA\"}");

        crear(usuario, comida(hoy, "10:30", "MEDIA_MANANA", yogur.getId(), "125", "g"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avisos").isEmpty());
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private ResultActions crear(UsuarioConToken autor, String cuerpo) throws Exception {
        return mockMvc.perform(conToken(post(RUTA), autor).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void fijarPreferencias(UsuarioConToken autor, String preferencia) throws Exception {
        mockMvc.perform(conToken(put("/api/v1/perfil/preferencias"), autor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferencias\": [" + preferencia + "]}"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion,
            UsuarioConToken autor) {
        return peticion.header("Authorization", autor.cabeceraAutorizacion());
    }

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    /** 200 g de arroz y 150 g de pollo. */
    private String comidaArrozYPollo(LocalDate fecha, String tipoComida) {
        return """
                {"fecha": "%s", "hora": "14:00", "tipoComida": "%s", "alimentos": [
                    {"alimentoId": "%s", "cantidad": 200, "unidad": "g"},
                    {"alimentoId": "%s", "cantidad": 150, "unidad": "g"}
                ]}""".formatted(fecha, tipoComida, arroz.getId(), pollo.getId());
    }

    private static String comida(LocalDate fecha, String hora, String tipoComida, UUID alimentoId, String cantidad,
            String unidad) {
        return """
                {"fecha": "%s", "hora": "%s", "tipoComida": "%s", "alimentos": [
                    {"alimentoId": "%s", "cantidad": %s, "unidad": "%s"}
                ]}""".formatted(fecha, hora, tipoComida, alimentoId, cantidad, unidad);
    }

    /** Alimento público con los alérgenos ya revisados, para que no aparezca el aviso "sin revisar". */
    private Alimento guardarAlimento(String nombre, String kcal, String proteinas, String carbohidratos,
            String grasas, List<String> alergenosContiene, List<String> alergenosTrazas, boolean contieneLactosa) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_CEREALES).orElseThrow());
        alimento.setKcal100(new BigDecimal(kcal));
        alimento.setProteinas100(new BigDecimal(proteinas));
        alimento.setCarbohidratos100(new BigDecimal(carbohidratos));
        alimento.setGrasas100(new BigDecimal(grasas));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAlergenosContiene(alergenosContiene);
        alimento.setAlergenosTrazas(alergenosTrazas);
        alimento.setAlergenosRevisados(true);
        alimento.setContieneLactosa(contieneLactosa);
        alimento.setAptoVegano(false);
        alimento.setAptoVegetariano(true);
        alimento.setAptoPescetariano(true);
        alimento.setFuente(FuenteDato.ADMIN);
        alimento.setReferenciaExterna("DEV");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        return alimentoRepositorio.save(alimento);
    }

    /** Las categorías iniciales (R__categorias.sql) se conservan. */
    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
