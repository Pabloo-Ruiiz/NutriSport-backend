package com.nutrisport.catalogo;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Búsqueda de alimentos tolerante a tildes, mayúsculas y errores de escritura, visibilidad de los
 * alimentos privados y categorías iniciales (§3.C y §7.3).
 */
@AutoConfigureMockMvc
class AlimentoBusquedaTest extends IntegracionBase {

    /** Categorías de R__categorias.sql. */
    private static final UUID FRUTAS = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID CARNES = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final UUID CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");
    private static final UUID LEGUMBRES = UUID.fromString("c0000000-0000-0000-0000-000000000008");

    private final MockMvc mockMvc;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JdbcTemplate jdbc;

    private UsuarioConToken usuarioA;
    private UsuarioConToken usuarioB;
    private Alimento privadoDeA;

    @Autowired
    AlimentoBusquedaTest(MockMvc mockMvc, AlimentoRepositorio alimentoRepositorio,
            CategoriaRepositorio categoriaRepositorio, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        usuarioA = crearUsuarioYToken();
        usuarioB = crearUsuarioYToken();
        guardarPublico("Plátano", FRUTAS);
        guardarPublico("Plato de lentejas", LEGUMBRES);
        guardarPublico("Pan integral", CEREALES);
        guardarPublico("Pechuga de pollo", CARNES);
        privadoDeA = guardar("Batido casero de Ana", FRUTAS, Visibilidad.PRIVADO, usuarioA.usuario());
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda tolerante
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" encuentra Plátano en primera posición")
    @ValueSource(strings = {"platno", "PLATANO", "plátano"})
    void encuentraPlatanoEnPrimeraPosicion(String q) throws Exception {
        buscarComo(usuarioB, q)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].nombre").value("Plátano"))
                .andExpect(jsonPath("$.content[0].fuente").value("BEDCA"));
    }

    // ---------------------------------------------------------------------------------------------
    // Alimentos privados
    // ---------------------------------------------------------------------------------------------

    @Test
    void unAlimentoPrivadoSoloLoEncuentraSuPropietario() throws Exception {
        buscarComo(usuarioA, "batido")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", hasItem("Batido casero de Ana")));

        buscarComo(usuarioB, "batido")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", not(hasItem("Batido casero de Ana"))));
    }

    @Test
    void elDetalleDeUnAlimentoPrivadoDeOtroUsuarioDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/alimentos/{id}", privadoDeA.getId())
                        .header("Authorization", usuarioA.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propio").value(true));

        mockMvc.perform(get("/api/v1/alimentos/{id}", privadoDeA.getId())
                        .header("Authorization", usuarioB.cabeceraAutorizacion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    // ---------------------------------------------------------------------------------------------
    // Categorías
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasCategoriasDeAlimentoIncluyenLasIniciales() throws Exception {
        mockMvc.perform(get("/api/v1/categorias").param("tipo", "ALIMENTO")
                        .header("Authorization", usuarioB.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$[0].nombre").value("Frutas"))
                .andExpect(jsonPath("$[0].seccionCompra").value("Frutas y verduras"));
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private ResultActions buscarComo(UsuarioConToken usuario, String q) throws Exception {
        return mockMvc.perform(get("/api/v1/alimentos").param("q", q)
                .header("Authorization", usuario.cabeceraAutorizacion()));
    }

    private Alimento guardarPublico(String nombre, UUID categoriaId) {
        return guardar(nombre, categoriaId, Visibilidad.PUBLICO, null);
    }

    private Alimento guardar(String nombre, UUID categoriaId, Visibilidad visibilidad, Usuario propietario) {
        Categoria categoria = categoriaRepositorio.findById(categoriaId).orElseThrow();
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoria);
        alimento.setKcal100(new BigDecimal("100"));
        alimento.setProteinas100(new BigDecimal("5"));
        alimento.setCarbohidratos100(new BigDecimal("15"));
        alimento.setGrasas100(new BigDecimal("2"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAptoVegano(false);
        alimento.setAptoVegetariano(false);
        alimento.setAptoPescetariano(false);
        alimento.setVisibilidad(visibilidad);
        alimento.setPropietario(propietario);
        alimento.setFuente(propietario == null ? FuenteDato.BEDCA : FuenteDato.USUARIO);
        alimento.setReferenciaExterna(propietario == null ? "DEV" : null);
        return alimentoRepositorio.save(alimento);
    }

    /** Borra alimentos y usuarios; las categorías iniciales (R__categorias.sql) se conservan. */
    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
