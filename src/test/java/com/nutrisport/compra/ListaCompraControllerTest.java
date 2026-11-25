package com.nutrisport.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * API de listas de la compra (§7.6) contra PostgreSQL real: generar desde un menú, progreso, productos manuales,
 * ingredientes de una receta y aislamiento entre usuarios.
 */
@AutoConfigureMockMvc
class ListaCompraControllerTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/listas-compra";
    private static final UUID FRUTAS = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID CARNES = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final UUID CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");
    private static final String PREFIJO_REFERENCIA = "TEST-COMPRA-";
    private static final LocalDate LUNES = LocalDate.of(2030, 1, 7);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private UsuarioConToken luis;
    private Alimento arroz;
    private Receta conManzana;
    private MenuSemanal menu;

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken();
        luis = crearUsuarioYToken();
        arroz = alimento("Arroz", CEREALES);
        Alimento pollo = alimento("Pechuga de pollo", CARNES);
        Alimento manzana = alimento("Manzana", FRUTAS);
        Receta conPollo = receta("Pollo con arroz", 1, List.of(arroz, pollo), List.of("200", "600"));
        conManzana = receta("Arroz con manzana", 1, List.of(arroz, manzana), List.of("150", "300"));
        conManzana.anadirIngrediente(new RecetaIngrediente("salt", "1 pinch"));
        conManzana = recetaRepositorio.saveAndFlush(conManzana);

        menu = new MenuSemanal();
        menu.setUsuario(ana.usuario());
        menu.setNombre("Semana de carga");
        menu.setTipo(TipoMenu.SEMANAL);
        menu.setFechaInicio(LUNES);
        menu.setFechaFin(LUNES.plusDays(6));
        menu.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, conPollo, BigDecimal.ONE));
        menu.anadirComida(new ComidaPlanificada(LUNES.plusDays(2), TipoComida.COMIDA, conManzana, BigDecimal.ONE));
        menu = menuRepositorio.saveAndFlush(menu);
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE lista_compra, menu_semanal, receta, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa LIKE ?", PREFIJO_REFERENCIA + "%");
    }

    @Test
    void generarDesdeUnMenuAgrupaPorSeccionesEnElOrdenDeLasCategorias() throws Exception {
        String json = cuerpo(generar(ana, null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern(RUTA + "/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.nombre").value("Compra: Semana de carga"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.menuId").value(menu.getId().toString()))
                .andExpect(jsonPath("$.fechaDesde").value(LUNES.toString()))
                .andExpect(jsonPath("$.fechaHasta").value(LUNES.plusDays(6).toString()))
                .andExpect(jsonPath("$.comprados").value(0))
                .andExpect(jsonPath("$.total").value(3)));

        // Frutas y verduras (1), Carnes (3), Despensa (7)
        assertThat(JsonPath.<List<String>>read(json, "$.secciones[*].nombre"))
                .containsExactly("Frutas y verduras", "Carnes", "Despensa");
        Map<String, Object> arrozGenerado = producto(json, "Arroz");
        assertThat(new BigDecimal(arrozGenerado.get("cantidad").toString())).isEqualByComparingTo("350");
        assertThat(arrozGenerado.get("unidad")).isEqualTo("G");
        assertThat(arrozGenerado.get("manual")).isEqualTo(false);
        assertThat(JsonPath.<List<String>>read(arrozGenerado, "$.desglose[*].fecha"))
                .containsExactly(LUNES.toString(), LUNES.plusDays(2).toString());
        // El ingrediente sin emparejar no entra en la lista del menú
        assertThat(JsonPath.<List<String>>read(json, "$.secciones[*].productos[*].nombre"))
                .containsExactlyInAnyOrder("Manzana", "Pechuga de pollo", "Arroz");

        // Solo el miércoles
        generar(ana, "{\"desde\": \"" + LUNES.plusDays(2) + "\", \"hasta\": \"" + LUNES.plusDays(2) + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.secciones[*].nombre").value(org.hamcrest.Matchers.contains(
                        "Frutas y verduras", "Despensa")));
        // Un rango fuera del menú o al revés
        generar(ana, "{\"desde\": \"" + LUNES.minusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("desde"));
        generar(ana, "{\"desde\": \"" + LUNES.plusDays(3) + "\", \"hasta\": \"" + LUNES.plusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("hasta"));

        mockMvc.perform(con(get(RUTA), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].total").value(2));
    }

    @Test
    void marcarDosCompradosActualizaElProgresoYLosPoneAlFinalDeSuSeccion() throws Exception {
        String json = cuerpo(generar(ana, null).andExpect(status().isCreated()));
        String id = JsonPath.read(json, "$.id");
        String manzana = (String) producto(json, "Manzana").get("id");
        String pollo = (String) producto(json, "Pechuga de pollo").get("id");

        marcar(ana, id, manzana, true).andExpect(status().isOk()).andExpect(jsonPath("$.comprados").value(1));
        marcar(ana, id, pollo, true).andExpect(status().isOk()).andExpect(jsonPath("$.comprados").value(2));

        mockMvc.perform(con(get(RUTA + "/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprados").value(2))
                .andExpect(jsonPath("$.total").value(3));
        mockMvc.perform(con(get(RUTA), ana))
                .andExpect(jsonPath("$.content[0].comprados").value(2))
                .andExpect(jsonPath("$.content[0].total").value(3));

        // Desmarcar uno y completar la lista
        marcar(ana, id, pollo, false).andExpect(jsonPath("$.comprados").value(1));
        mockMvc.perform(con(patch(RUTA + "/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Compra del lunes\", \"estado\": \"COMPLETADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Compra del lunes"))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));

        // Un comprado va al final de su sección
        String conLibre = cuerpo(anadirProducto(ana, id, "Fruta de temporada", "Frutas y verduras")
                .andExpect(status().isCreated()));
        assertThat(JsonPath.<List<String>>read(conLibre,
                "$.secciones[?(@.nombre == 'Frutas y verduras')].productos[*].nombre"))
                .containsExactly("Fruta de temporada", "Manzana");
    }

    @Test
    void anadirUnProductoManualYLosIngredientesDeUnaReceta() throws Exception {
        String id = JsonPath.read(cuerpo(generar(ana, null)), "$.id");

        // Sin sección va a "Otros"; "despensa" se escribe como la de la categoría
        anadirProducto(ana, id, "Servilletas", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.secciones[-1].nombre").value("Otros"));
        String json = cuerpo(anadirProducto(ana, id, "Papel de horno", "despensa").andExpect(status().isCreated()));
        Map<String, Object> papel = producto(json, "Papel de horno");
        assertThat(papel.get("seccion")).isEqualTo("Despensa");
        assertThat(papel.get("manual")).isEqualTo(true);
        assertThat(papel.get("unidad")).isEqualTo("UD");
        assertThat(JsonPath.<List<Object>>read(papel, "$.desglose")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM producto_compra WHERE manual", Long.class)).isEqualTo(2);

        // Receta × 2 raciones: el arroz pendiente suma 300 g (350 → 650), la manzana y la sal sin emparejar
        String conReceta = cuerpo(mockMvc.perform(con(post(RUTA + "/" + id + "/productos/desde-receta"), ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recetaId\": \"" + conManzana.getId() + "\", \"raciones\": 2}"))
                .andExpect(status().isCreated()));
        Map<String, Object> arrozSumado = producto(conReceta, "Arroz");
        assertThat(new BigDecimal(arrozSumado.get("cantidad").toString())).isEqualByComparingTo("650");
        assertThat(arrozSumado.get("manual")).isEqualTo(false);
        assertThat(new BigDecimal(producto(conReceta, "Manzana").get("cantidad").toString()))
                .isEqualByComparingTo("900");
        Map<String, Object> sal = producto(conReceta, "salt (1 pinch)");
        assertThat(sal.get("unidad")).isEqualTo("UD");
        assertThat(sal.get("seccion")).isEqualTo("Otros");

        // Validación
        mockMvc.perform(con(post(RUTA + "/" + id + "/productos"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \" \", \"cantidad\": 0, \"unidad\": \"CAJAS\"}"))
                .andExpect(status().isBadRequest());

        // Quitar un producto
        String papelId = (String) papel.get("id");
        mockMvc.perform(con(delete(RUTA + "/" + id + "/productos/" + papelId), ana))
                .andExpect(status().isNoContent());
        mockMvc.perform(con(get(RUTA + "/" + id), ana))
                .andExpect(jsonPath("$.secciones[*].productos[*].nombre", org.hamcrest.Matchers.not(
                        hasItem("Papel de horno"))));
    }

    @Test
    void lasListasYLosMenusDeOtroUsuarioResponden404() throws Exception {
        String json = cuerpo(generar(ana, null));
        String id = JsonPath.read(json, "$.id");
        String productoId = (String) producto(json, "Arroz").get("id");

        mockMvc.perform(con(get(RUTA + "/" + id), luis)).andExpect(status().isNotFound());
        mockMvc.perform(con(patch(RUTA + "/" + id), luis).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"COMPLETADA\"}")).andExpect(status().isNotFound());
        mockMvc.perform(con(delete(RUTA + "/" + id), luis)).andExpect(status().isNotFound());
        anadirProducto(luis, id, "Intruso", null).andExpect(status().isNotFound());
        marcar(luis, id, productoId, true).andExpect(status().isNotFound());
        mockMvc.perform(con(delete(RUTA + "/" + id + "/productos/" + productoId), luis))
                .andExpect(status().isNotFound());
        generar(luis, null).andExpect(status().isNotFound());
        mockMvc.perform(con(get(RUTA), luis)).andExpect(jsonPath("$.content.length()").value(0));

        // Nada ha cambiado para Ana
        mockMvc.perform(con(get(RUTA + "/" + id), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.comprados").value(0));

        // Un producto de otra lista tampoco existe en esta
        String otraLista = JsonPath.read(cuerpo(generar(ana, null)), "$.id");
        marcar(ana, otraLista, productoId, true).andExpect(status().isNotFound());

        // Ana borra su lista
        mockMvc.perform(con(delete(RUTA + "/" + id), ana)).andExpect(status().isNoContent());
        mockMvc.perform(con(get(RUTA + "/" + id), ana)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------------

    private ResultActions generar(UsuarioConToken usuario, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder peticion = con(post(RUTA + "/desde-menu/" + menu.getId()), usuario);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions marcar(UsuarioConToken usuario, String listaId, String productoId, boolean comprado)
            throws Exception {
        return mockMvc.perform(con(patch(RUTA + "/" + listaId + "/productos/" + productoId), usuario)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comprado\": " + comprado + "}"));
    }

    private ResultActions anadirProducto(UsuarioConToken usuario, String listaId, String nombre, String seccion)
            throws Exception {
        String cuerpo = "{\"nombre\": \"" + nombre + "\", \"cantidad\": 1, \"unidad\": \"UD\""
                + (seccion == null ? "" : ", \"seccion\": \"" + seccion + "\"") + "}";
        return mockMvc.perform(con(post(RUTA + "/" + listaId + "/productos"), usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** El único producto pendiente con ese nombre (o el único, si no hay pendientes). */
    private static Map<String, Object> producto(String json, String nombre) {
        List<Map<String, Object>> encontrados = JsonPath.read(json,
                "$.secciones[*].productos[?(@.nombre == '" + nombre + "')]");
        assertThat(encontrados).as(nombre).isNotEmpty();
        return encontrados.stream().filter(p -> Boolean.FALSE.equals(p.get("comprado"))).findFirst()
                .orElse(encontrados.get(0));
    }

    private Alimento alimento(String nombre, UUID categoria) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.getReferenceById(categoria));
        alimento.setKcal100(new BigDecimal("100"));
        alimento.setProteinas100(BigDecimal.ONE);
        alimento.setCarbohidratos100(BigDecimal.ONE);
        alimento.setGrasas100(BigDecimal.ONE);
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setFuente(FuenteDato.BEDCA);
        alimento.setReferenciaExterna(PREFIJO_REFERENCIA + nombre);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    private Receta receta(String nombre, int raciones, List<Alimento> alimentos, List<String> gramos) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setRaciones((short) raciones);
        for (int i = 0; i < alimentos.size(); i++) {
            RecetaIngrediente ingrediente = new RecetaIngrediente(alimentos.get(i).getNombre(), gramos.get(i) + " g");
            ingrediente.setAlimento(alimentos.get(i));
            ingrediente.setCantidadGramos(new BigDecimal(gramos.get(i)));
            ingrediente.setEstadoConversion(EstadoConversion.EXACTA);
            receta.anadirIngrediente(ingrediente);
        }
        return recetaRepositorio.saveAndFlush(receta);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, UsuarioConToken usuario) {
        return peticion.header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion());
    }

    private static String cuerpo(ResultActions acciones) throws Exception {
        return acciones.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
