package com.nutrisport.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.web.CookieRefresh;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Administración básica: acceso por rol, gestión de usuarios y revisión y baja de alimentos. */
@AutoConfigureMockMvc
class AdminControllerTest extends IntegracionBase {

    private static final UUID CATEGORIA_CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");
    private static final String PASSWORD = "contraseña-de-prueba-larga";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final LimitadorPeticiones limitador;
    private final UsuarioRepositorio usuarioRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final Clock reloj;

    private UsuarioConToken admin;
    private UsuarioConToken deportista;

    @Autowired
    AdminControllerTest(MockMvc mockMvc, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            LimitadorPeticiones limitador, UsuarioRepositorio usuarioRepositorio,
            AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio, Clock reloj) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.limitador = limitador;
        this.usuarioRepositorio = usuarioRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        limitador.reiniciarTodo();
        admin = crearAdminYToken();
        deportista = crearUsuarioYToken();
    }

    /** Las peticiones confirman sus transacciones: se borran los datos para no afectar a otros tests. */
    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    // ---------------------------------------------------------------------------------------------
    // Usuarios
    // ---------------------------------------------------------------------------------------------

    @Test
    void unDeportistaNoEntraEnLaAdministracion() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios").header("Authorization", deportista.cabeceraAutorizacion()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void elAdminListaYBuscaUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios").header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Búsqueda por parte del email, sin distinguir mayúsculas
        mockMvc.perform(get("/api/v1/admin/usuarios").param("q", "DEPORTISTA-")
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(deportista.usuario().getEmail()));

        // Filtro por estado
        mockMvc.perform(get("/api/v1/admin/usuarios").param("estado", "BLOQUEADO")
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void laRespuestaDeUnUsuarioSoloTieneDatosDeCuenta() throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/usuarios/{id}", deportista.usuario().getId())
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(deportista.usuario().getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("DEPORTISTA"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> usuario = JsonPath.read(json, "$");
        assertThat(usuario.keySet()).containsExactlyInAnyOrder("id", "nombre", "email", "roles", "estado",
                "emailVerificado", "ultimoAcceso", "creadoEn");
        // Ni datos del perfil deportivo ni secretos de la cuenta
        assertThat(usuario).doesNotContainKeys("pesoActualKg", "alturaCm", "fechaNacimiento", "sexo", "objetivo",
                "alergias", "passwordHash");
    }

    @Test
    void bloquearAUnUsuarioInvalidaSuLoginYSuRefresh() throws Exception {
        ponerPassword(deportista);
        String cookieRefresh = cookieRefresh(login(deportista).andExpect(status().isOk()).andReturn());

        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}", deportista.usuario().getId())
                        .header("Authorization", admin.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"BLOQUEADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADO"));

        login(deportista).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, cookieRefresh)))
                .andExpect(status().isUnauthorized());
        // El access token que ya tenía deja de valer en la siguiente petición
        mockMvc.perform(get("/api/v1/cuenta").header("Authorization", deportista.cabeceraAutorizacion()))
                .andExpect(status().isUnauthorized());
        assertThat(contarAuditoria("ACTUALIZAR_USUARIO")).isEqualTo(1);
    }

    @Test
    void elAdminNoPuedeBloquearseASiMismo() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/usuarios/{id}", admin.usuario().getId())
                        .header("Authorization", admin.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"BLOQUEADO\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(usuarioRepositorio.findById(admin.usuario().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(contarAuditoria("ACTUALIZAR_USUARIO")).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Alimentos
    // ---------------------------------------------------------------------------------------------

    @Test
    void revisarLosAlergenosLosMarcaRevisadosYQuedaAuditado() throws Exception {
        Alimento pan = guardarAlimentoSinRevisar("Pan de trigo integral");

        mockMvc.perform(get("/api/v1/admin/alimentos").param("revisados", "false")
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(pan.getId().toString())));

        mockMvc.perform(put("/api/v1/admin/alimentos/{id}/alergenos", pan.getId())
                        .header("Authorization", admin.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"alergenosContiene": ["GLUTEN"], "alergenosTrazas": ["SESAMO"],
                                 "contieneLactosa": false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergenosRevisados").value(true))
                .andExpect(jsonPath("$.alergenosContiene[0]").value("GLUTEN"))
                .andExpect(jsonPath("$.alergenosTrazas[0]").value("SESAMO"));

        assertThat(alimentoRepositorio.findById(pan.getId()).orElseThrow().isAlergenosRevisados()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE accion = 'REVISAR_ALERGENOS' AND entidad_id = ? AND admin_id = ?",
                Integer.class, pan.getId().toString(), admin.usuario().getId())).isEqualTo(1);

        // Ya no aparece entre los pendientes de revisar
        mockMvc.perform(get("/api/v1/admin/alimentos").param("revisados", "false")
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id")
                        .value(not(hasItem(pan.getId().toString()))));
    }

    @Test
    void eliminarUnAlimentoUsadoEnUnaComidaLoDesactiva() throws Exception {
        Alimento avena = guardarAlimentoSinRevisar("Copos de avena");
        registrarComida(avena.getId());
        assertThat(idsEnBusquedaPublica("avena")).contains(avena.getId().toString());

        mockMvc.perform(delete("/api/v1/admin/alimentos/{id}", avena.getId())
                        .header("Authorization", admin.cabeceraAutorizacion()))
                .andExpect(status().isNoContent());

        // Se conserva por las comidas que lo usan, pero inactivo
        assertThat(alimentoRepositorio.findById(avena.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(contarAuditoria("DESACTIVAR_ALIMENTO")).isEqualTo(1);
        assertThat(idsEnBusquedaPublica("avena")).doesNotContain(avena.getId().toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Los usuarios de IntegracionBase no pueden hacer login: se les pone una contraseña real. */
    private void ponerPassword(UsuarioConToken usuario) {
        jdbc.update("UPDATE usuario SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD),
                usuario.usuario().getId());
    }

    private ResultActions login(UsuarioConToken usuario) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"mantenerSesion\":false}"
                        .formatted(usuario.usuario().getEmail(), PASSWORD)));
    }

    private static String cookieRefresh(MvcResult resultado) {
        String cabecera = resultado.getResponse().getHeader("Set-Cookie");
        assertThat(cabecera).startsWith(CookieRefresh.NOMBRE + "=");
        return cabecera.substring(CookieRefresh.NOMBRE.length() + 1, cabecera.indexOf(';'));
    }

    private void registrarComida(UUID alimentoId) throws Exception {
        mockMvc.perform(post("/api/v1/comidas")
                        .header("Authorization", deportista.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "%s", "tipoComida": "DESAYUNO", "alimentos": [
                                    {"alimentoId": "%s", "cantidad": 60, "unidad": "g"}
                                ]}""".formatted(LocalDate.now(reloj), alimentoId)))
                .andExpect(status().isCreated());
    }

    private List<String> idsEnBusquedaPublica(String q) throws Exception {
        String json = mockMvc.perform(get("/api/v1/alimentos").param("q", q)
                        .header("Authorization", deportista.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content[*].id");
    }

    private int contarAuditoria(String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = ?", Integer.class, accion);
    }

    private Alimento guardarAlimentoSinRevisar(String nombre) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_CEREALES).orElseThrow());
        alimento.setKcal100(new BigDecimal("370"));
        alimento.setProteinas100(new BigDecimal("13.5"));
        alimento.setCarbohidratos100(new BigDecimal("58.7"));
        alimento.setGrasas100(new BigDecimal("7.0"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAlergenosRevisados(false);
        alimento.setAptoVegetariano(true);
        alimento.setAptoPescetariano(true);
        alimento.setFuente(FuenteDato.ADMIN);
        alimento.setReferenciaExterna("DEV");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        return alimentoRepositorio.save(alimento);
    }

    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
