package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.auth.servicio.PurgaCuentasTarea;
import com.nutrisport.auth.web.CookieRefresh;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.almacenamiento.ClavesAlmacenamiento;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.comun.config.InicializadorAdmin;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Gestión de la cuenta, consentimiento de salud, eliminación, purga y administrador inicial.
 * <p>
 * El InterceptorConsentimiento se comprueba con {@code GET /api/v1/perfil/preferencias}, que responde
 * 200 con una lista vacía cuando deja pasar la petición.
 */
@AutoConfigureMockMvc
class CuentaControllerTest extends IntegracionBase {

    private static final String RUTA_SALUD_ACCESIBLE = "/api/v1/perfil/preferencias";

    private static final String EMAIL = "ana@ejemplo.com";
    private static final String PASSWORD = "Avena-y-platano-42";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JwtServicio jwtServicio;
    private final PurgaCuentasTarea purgaCuentasTarea;
    private final JdbcTemplate jdbc;
    private final LimitadorPeticiones limitador;
    private final AlmacenamientoServicio almacenamiento;

    @MockitoBean
    private CorreoServicio correoServicio;

    private Usuario ana;

    @Autowired
    CuentaControllerTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder,
            JwtServicio jwtServicio, PurgaCuentasTarea purgaCuentasTarea, JdbcTemplate jdbc,
            LimitadorPeticiones limitador, AlmacenamientoServicio almacenamiento) {
        this.mockMvc = mockMvc;
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.jwtServicio = jwtServicio;
        this.purgaCuentasTarea = purgaCuentasTarea;
        this.jdbc = jdbc;
        this.limitador = limitador;
        this.almacenamiento = almacenamiento;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        limitador.reiniciarTodo();
        ana = crearUsuario(EMAIL);
    }

    /** Las peticiones confirman sus transacciones: se borran los datos para no afectar a otros tests. */
    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Datos de la cuenta
    // ---------------------------------------------------------------------------------------------

    @Test
    void obtenerYActualizarLaCuenta() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/cuenta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(conToken(patch("/api/v1/cuenta")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  Ana María \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana María"))
                .andExpect(jsonPath("$.unidades").value("METRICO"));

        mockMvc.perform(conToken(patch("/api/v1/cuenta")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("nombre"));
        mockMvc.perform(get("/api/v1/cuenta")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------
    // Contraseña
    // ---------------------------------------------------------------------------------------------

    @Test
    void cambiarPasswordCompruebaLaActualYCierraLasSesiones() throws Exception {
        String refresh = refreshDeLogin(PASSWORD);

        cambiarPassword("Incorrecta-123", "Nueva-clave-segura-7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[0].campo").value("passwordActual"));
        cambiarPassword(PASSWORD, "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("nuevaPassword"));

        cambiarPassword(PASSWORD, "Nueva-clave-segura-7").andExpect(status().isNoContent());

        login(PASSWORD).andExpect(status().isUnauthorized());
        login("Nueva-clave-segura-7").andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, refresh)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------
    // Email
    // ---------------------------------------------------------------------------------------------

    @Test
    void cambiarEmailEnviaElEnlaceAlNuevoYAlConfirmarCambiaElEmail() throws Exception {
        cambiarEmail("Ana.Nueva@Ejemplo.com", PASSWORD).andExpect(status().isAccepted());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarCambioEmail(eq("ana.nueva@ejemplo.com"), eq("Ana"), token.capture());
        // Hasta confirmarlo sigue con el email anterior
        assertThat(usuarioRepositorio.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);

        verificar(token.getValue()).andExpect(status().isNoContent());

        Usuario actualizado = usuarioRepositorio.findById(ana.getId()).orElseThrow();
        assertThat(actualizado.getEmail()).isEqualTo("ana.nueva@ejemplo.com");
        assertThat(actualizado.emailVerificado()).isTrue();
        loginCon("ana.nueva@ejemplo.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuenta.email").value("ana.nueva@ejemplo.com"));
        loginCon(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        verificar(token.getValue())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_NO_VALIDO"));
    }

    @Test
    void cambiarEmailNoRevelaSiElEmailExisteYExigeLaContrasena() throws Exception {
        crearUsuario("ocupado@ejemplo.com");

        cambiarEmail("ocupado@ejemplo.com", PASSWORD).andExpect(status().isAccepted());
        verify(correoServicio, never()).enviarCambioEmail(anyString(), anyString(), anyString());

        cambiarEmail("libre@ejemplo.com", "Incorrecta-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("password"));
    }

    @Test
    void siElEmailSeOcupaAntesDeConfirmarDevuelve409() throws Exception {
        cambiarEmail("libre@ejemplo.com", PASSWORD).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarCambioEmail(eq("libre@ejemplo.com"), eq("Ana"), token.capture());

        crearUsuario("libre@ejemplo.com");

        verificar(token.getValue())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_EN_USO"));
        assertThat(usuarioRepositorio.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void elTokenDeRecuperacionNoSirveParaVerificarElEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/recuperar-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(EMAIL))).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarRecuperacion(eq(EMAIL), eq("Ana"), token.capture());

        verificar(token.getValue()).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------
    // Consentimiento de salud
    // ---------------------------------------------------------------------------------------------

    @Test
    void revocarElConsentimientoBloqueaLasRutasDeSaludHastaDarloDeNuevo() throws Exception {
        // Con consentimiento: el interceptor deja pasar
        mockMvc.perform(conToken(get(RUTA_SALUD_ACCESIBLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(conToken(delete("/api/v1/cuenta/consentimiento-salud"))).andExpect(status().isNoContent());

        for (String ruta : new String[] {"/api/v1/perfil", "/api/v1/comidas/123", "/api/v1/analisis",
            "/api/v1/entrenamientos", "/api/v1/seguimiento/resumen"}) {
            mockMvc.perform(conToken(get(ruta)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("CONSENTIMIENTO_REVOCADO"));
        }
        // La cuenta sigue accesible para poder exportar, eliminar o volver a dar el consentimiento
        mockMvc.perform(conToken(get("/api/v1/cuenta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentimientoSaludVigente").value(false));

        mockMvc.perform(conToken(post("/api/v1/cuenta/consentimiento-salud"))).andExpect(status().isNoContent());

        // Con el consentimiento de nuevo vigente, vuelve a haber acceso
        mockMvc.perform(conToken(get(RUTA_SALUD_ACCESIBLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        Usuario usuario = usuarioRepositorio.findById(ana.getId()).orElseThrow();
        assertThat(usuario.consentimientoVigente()).isTrue();
        assertThat(usuario.getConsentimientoSaludVersion()).isEqualTo("2026-11");
    }

    // ---------------------------------------------------------------------------------------------
    // Eliminación y purga
    // ---------------------------------------------------------------------------------------------

    @Test
    void eliminarDiferidoDejaLaCuentaEliminadaYBorraLaCookie() throws Exception {
        String refresh = refreshDeLogin(PASSWORD);
        String imagen = ClavesAlmacenamiento.claveComida(ana.getId(), LocalDate.now());
        byte[] foto = new byte[] {1, 2, 3};
        almacenamiento.guardar(imagen, foto, "image/jpeg");

        eliminar("Incorrecta-123", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("password"));
        assertThat(almacenamiento.descargar(imagen)).isEqualTo(foto);

        String cookie = eliminar(PASSWORD, false)
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("Max-Age=0");
        assertThatThrownBy(() -> almacenamiento.descargar(imagen))
                .isInstanceOf(ServicioNoDisponibleException.class);

        Usuario eliminado = usuarioRepositorio.findById(ana.getId()).orElseThrow();
        assertThat(eliminado.getEstado()).isEqualTo(EstadoUsuario.ELIMINADO);
        assertThat(eliminado.getEliminadoEn()).isNotNull();
        login(PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, refresh)))
                .andExpect(status().isUnauthorized());
        // El access token aún vigente ya no sirve: el filtro rechaza a los usuarios ELIMINADOS
        mockMvc.perform(conToken(get("/api/v1/cuenta"))).andExpect(status().isUnauthorized());
    }

    @Test
    void eliminarInmediatoBorraElUsuarioYSusDatos() throws Exception {
        refreshDeLogin(PASSWORD);
        String imagen = ClavesAlmacenamiento.claveComida(ana.getId(), LocalDate.now());
        String miniatura = ClavesAlmacenamiento.claveMiniatura(imagen);
        almacenamiento.guardar(imagen, new byte[] {1, 2, 3}, "image/jpeg");
        almacenamiento.guardar(miniatura, new byte[] {1, 2, 3}, "image/jpeg");

        eliminar(PASSWORD, true).andExpect(status().isNoContent());

        assertThat(usuarioRepositorio.findById(ana.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario_rol", Integer.class)).isZero();
        assertThatThrownBy(() -> almacenamiento.descargar(imagen))
                .isInstanceOf(ServicioNoDisponibleException.class);
        assertThatThrownBy(() -> almacenamiento.descargar(miniatura))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void laPurgaBorraA31DiasLaCuentaEliminadaPorLaApi() throws Exception {
        eliminar(PASSWORD, false).andExpect(status().isNoContent());
        Instant ahora = Instant.now();

        // A los 29 días todavía existe; a los 31 se borra
        assertThat(purgaCuentasTarea.ejecutar(ahora.plus(Duration.ofDays(29)))).isZero();
        assertThat(usuarioRepositorio.findById(ana.getId())).isPresent();

        assertThat(purgaCuentasTarea.ejecutar(ahora.plus(Duration.ofDays(31)))).isEqualTo(1);
        assertThat(usuarioRepositorio.findById(ana.getId())).isEmpty();
    }

    @Test
    void laPurgaBorraSoloLasCuentasEliminadasHaceMasDe30Dias() {
        Instant ahora = Instant.now();
        Usuario antigua = crearUsuario("antigua@ejemplo.com");
        Usuario reciente = crearUsuario("reciente@ejemplo.com");
        marcarEliminado(antigua, ahora.minus(Duration.ofDays(31)));
        marcarEliminado(reciente, ahora.minus(Duration.ofDays(29)));

        assertThat(purgaCuentasTarea.ejecutar(ahora)).isEqualTo(1);

        assertThat(usuarioRepositorio.findById(antigua.getId())).isEmpty();
        assertThat(usuarioRepositorio.findById(reciente.getId())).isPresent();
        assertThat(usuarioRepositorio.findById(ana.getId())).isPresent();
    }

    // ---------------------------------------------------------------------------------------------
    // Administrador inicial
    // ---------------------------------------------------------------------------------------------

    @Test
    void elInicializadorCreaElAdminUnaSolaVezYSinVariablesNoHaceNada() throws Exception {
        new InicializadorAdmin(usuarioRepositorio, passwordEncoder, "", "", "2026-11").run(null);
        assertThat(usuarioRepositorio.count()).isEqualTo(1);

        InicializadorAdmin inicializador = new InicializadorAdmin(usuarioRepositorio, passwordEncoder,
                " Admin@NutriSport.local ", "Clave-admin-de-prueba-1", "2026-11");
        inicializador.run(null);
        inicializador.run(null);

        Usuario admin = usuarioRepositorio.findByEmail("admin@nutrisport.local").orElseThrow();
        assertThat(admin.getRoles()).containsExactlyInAnyOrder(Rol.ADMIN, Rol.DEPORTISTA);
        assertThat(admin.estaActivo()).isTrue();
        assertThat(admin.emailVerificado()).isTrue();
        assertThat(passwordEncoder.matches("Clave-admin-de-prueba-1", admin.getPasswordHash())).isTrue();
        assertThat(usuarioRepositorio.count()).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        String token = jwtServicio.generarAccessToken(UsuarioAutenticado.de(ana));
        return peticion.header("Authorization", "Bearer " + token);
    }

    private ResultActions cambiarPassword(String actual, String nueva) throws Exception {
        return mockMvc.perform(conToken(put("/api/v1/cuenta/password")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"%s\",\"nuevaPassword\":\"%s\"}".formatted(actual, nueva)));
    }

    private ResultActions cambiarEmail(String nuevoEmail, String password) throws Exception {
        return mockMvc.perform(conToken(put("/api/v1/cuenta/email")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nuevoEmail\":\"%s\",\"password\":\"%s\"}".formatted(nuevoEmail, password)));
    }

    private ResultActions eliminar(String password, boolean inmediata) throws Exception {
        return mockMvc.perform(conToken(delete("/api/v1/cuenta")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\",\"inmediata\":%s}".formatted(password, inmediata)));
    }

    private ResultActions verificar(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verificar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions login(String password) throws Exception {
        return loginCon(EMAIL, password);
    }

    private ResultActions loginCon(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"mantenerSesion\":false}".formatted(email, password)));
    }

    private String refreshDeLogin(String password) throws Exception {
        String cookie = login(password).andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        return cookie.substring(CookieRefresh.NOMBRE.length() + 1, cookie.indexOf(';'));
    }

    private void marcarEliminado(Usuario usuario, Instant cuando) {
        Usuario recargado = usuarioRepositorio.findById(usuario.getId()).orElseThrow();
        recargado.setEstado(EstadoUsuario.ELIMINADO);
        recargado.setEliminadoEn(OffsetDateTime.ofInstant(cuando, ZoneOffset.UTC));
        usuarioRepositorio.save(recargado);
    }

    private Usuario crearUsuario(String email) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("2026-11");
        usuario.anadirRol(Rol.DEPORTISTA);
        return usuarioRepositorio.save(usuario);
    }
}
