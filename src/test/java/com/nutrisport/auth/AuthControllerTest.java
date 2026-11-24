package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.ThreadLocalRandom;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.RefreshTokenRepositorio;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.web.CookieRefresh;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import com.nutrisport.comun.util.Tokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Registro, verificación, login, sesiones, recuperación y límites de intentos contra PostgreSQL,
 * con el correo simulado.
 * <p>
 * No es {@code @Transactional} a propósito: {@code AuthServicio} envía los correos después del
 * commit, y en un test transaccional (que se deshace al terminar) nunca se enviarían. Las tablas se
 * vacían antes de cada test.
 */
@AutoConfigureMockMvc
class AuthControllerTest extends IntegracionBase {

    private static final String PASSWORD = "Avena-y-platano-42";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final RefreshTokenRepositorio refreshTokenRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    private final LimitadorPeticiones limitador;

    @MockitoBean
    private CorreoServicio correoServicio;

    @Autowired
    AuthControllerTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio,
            RefreshTokenRepositorio refreshTokenRepositorio, PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
            LimitadorPeticiones limitador) {
        this.mockMvc = mockMvc;
        this.usuarioRepositorio = usuarioRepositorio;
        this.refreshTokenRepositorio = refreshTokenRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.limitador = limitador;
    }

    @BeforeEach
    void limpiarAntes() {
        vaciarTablas();
        Mockito.reset(correoServicio);
        // El limitador vive en memoria y lo comparten todos los tests del contexto
        limitador.reiniciarTodo();
    }

    /** Este test confirma sus datos: hay que borrarlos para no afectar a otras clases de test. */
    @AfterEach
    void limpiarDespues() {
        vaciarTablas();
    }

    private void vaciarTablas() {
        // Borra también usuario_rol, refresh_token y token_usuario (ON DELETE CASCADE)
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------------------------------

    @Test
    void registroCorrectoDevuelve202YEnviaLaVerificacion() throws Exception {
        registrar("Ana@Ejemplo.COM", PASSWORD, true, true)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        String token = tokenEnviado("ana@ejemplo.com");

        Usuario usuario = usuarioRepositorio.findByEmail("ana@ejemplo.com").orElseThrow();
        assertThat(usuario.getNombre()).isEqualTo("Ana");
        assertThat(usuario.getRoles()).containsExactly(Rol.DEPORTISTA);
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(usuario.emailVerificado()).isFalse();
        assertThat(usuario.getConsentimientoSaludVersion()).isEqualTo("2026-11");
        assertThat(passwordEncoder.matches(PASSWORD, usuario.getPasswordHash())).isTrue();
        // En la base de datos solo se guarda el hash del token
        assertThat(jdbc.queryForObject("SELECT token_hash FROM token_usuario", String.class))
                .isEqualTo(Tokens.sha256Hex(token));
    }

    @Test
    void registroConElMismoEmailRespondeIgualYAvisaAlTitular() throws Exception {
        String respuestaNueva = registrar("ana@ejemplo.com", PASSWORD, true, true)
                .andReturn().getResponse().getContentAsString();
        Mockito.reset(correoServicio);

        String respuestaRepetida = registrar("ANA@ejemplo.com", PASSWORD, true, true)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuestaRepetida).isEqualTo(respuestaNueva);
        verify(correoServicio).enviarAvisoCuentaExistente("ana@ejemplo.com");
        verify(correoServicio, never()).enviarVerificacion(anyString(), anyString(), anyString());
        assertThat(usuarioRepositorio.count()).isEqualTo(1);
    }

    @Test
    void registroSinConsentimientoSaludDevuelve400() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'consentimientoSalud')]").exists());
        assertThat(usuarioRepositorio.count()).isZero();
    }

    @Test
    void registroSinMayorDeEdadDevuelve400() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, false, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'mayorDeEdad')]").exists());
        assertThat(usuarioRepositorio.count()).isZero();
    }

    @Test
    void registroConContrasenaComunDevuelve400() throws Exception {
        registrar("ana@ejemplo.com", "password123", true, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'password')].mensaje")
                        .value("Es una contraseña demasiado común"));
        assertThat(usuarioRepositorio.count()).isZero();
    }

    @Test
    void registroValidaNombreYEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"","email":"no-es-email","password":"%s",
                         "mayorDeEdad":true,"aceptaTerminos":true,"consentimientoSalud":true}
                        """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.campo == 'nombre')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo == 'email')]").exists());
    }

    // ---------------------------------------------------------------------------------------------
    // Verificación de email
    // ---------------------------------------------------------------------------------------------

    @Test
    void verificarConElTokenEnviadoDevuelve204YLaCuentaQuedaVerificada() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String token = tokenEnviado("ana@ejemplo.com");

        verificar(token).andExpect(status().isNoContent());

        // No existe GET /auth/yo: se comprueba en la cuenta que devuelve el login
        login("ana@ejemplo.com", PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuenta.emailVerificado").value(true));
    }

    @Test
    void verificarDosVecesDevuelve400TokenNoValido() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String token = tokenEnviado("ana@ejemplo.com");

        verificar(token).andExpect(status().isNoContent());

        verificar(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_NO_VALIDO"));
        verificar("token-inventado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_NO_VALIDO"));
    }

    @Test
    void reenviarInvalidaElEnlaceAnteriorYNoRevelaSiLaCuentaExiste() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String primero = tokenEnviado("ana@ejemplo.com");
        Mockito.reset(correoServicio);

        reenviar("ana@ejemplo.com").andExpect(status().isAccepted());
        String segundo = tokenEnviado("ana@ejemplo.com");
        verificar(primero).andExpect(status().isBadRequest());
        verificar(segundo).andExpect(status().isNoContent());

        Mockito.reset(correoServicio);
        String respuestaInexistente = reenviar("nadie@ejemplo.com")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String respuestaYaVerificada = reenviar("ana@ejemplo.com")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(respuestaInexistente).isEqualTo(respuestaYaVerificada);
        verify(correoServicio, never()).enviarVerificacion(anyString(), anyString(), anyString());
    }

    // ---------------------------------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------------------------------

    @Test
    void loginCorrectoDevuelveAccessTokenYCookieDeSesion() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);

        MvcResult resultado = login("ANA@ejemplo.com", PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(900))
                .andExpect(jsonPath("$.cuenta.email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$.cuenta.emailVerificado").value(false))
                .andExpect(jsonPath("$.cuenta.passwordHash").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String cookie = resultado.getResponse().getHeader("Set-Cookie");
        assertThat(cookie)
                .startsWith(CookieRefresh.NOMBRE + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth")
                .doesNotContain("Max-Age");

        // En la base de datos solo está el hash del refresh token
        String refresh = cookie.substring(CookieRefresh.NOMBRE.length() + 1, cookie.indexOf(';'));
        assertThat(refreshTokenRepositorio.findByTokenHash(Tokens.sha256Hex(refresh))).isPresent();
        assertThat(usuarioRepositorio.findByEmail("ana@ejemplo.com").orElseThrow().getUltimoAcceso()).isNotNull();

        // El access token sirve en una ruta protegida
        String accessToken = JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/api/v1/cuenta").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void loginConMantenerSesionLlevaMaxAge() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);

        String cookie = login("ana@ejemplo.com", PASSWORD, true)
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");

        assertThat(cookie).contains("HttpOnly", "SameSite=Strict", "Max-Age=604800");
    }

    @Test
    void loginConContrasenaErroneaDevuelve401() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);

        login("ana@ejemplo.com", "Contrasena-incorrecta-1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void loginDaElMismoErrorParaUsuarioInexistenteBloqueadoOContrasenaDemasiadoLarga() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        registrar("bloqueado@ejemplo.com", PASSWORD, true, true);
        Usuario bloqueado = usuarioRepositorio.findByEmail("bloqueado@ejemplo.com").orElseThrow();
        bloqueado.setEstado(EstadoUsuario.BLOQUEADO);
        usuarioRepositorio.save(bloqueado);

        for (String[] credenciales : new String[][] {
            {"nadie@ejemplo.com", PASSWORD},
            {"bloqueado@ejemplo.com", PASSWORD},
            {"ana@ejemplo.com", "x".repeat(100) + "1"}
        }) {
            login(credenciales[0], credenciales[1], false)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"));
        }
        assertThat(refreshTokenRepositorio.count()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Refresh y logout
    // ---------------------------------------------------------------------------------------------

    @Test
    void refreshRotaLaCookieYConservaLaPersistencia() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String sesion = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, false).andReturn());
        String persistente = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, true).andReturn());

        MvcResult rotada = refrescar(sesion, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.cuenta.email").value("ana@ejemplo.com"))
                .andReturn();
        assertThat(rotada.getResponse().getHeader("Set-Cookie")).doesNotContain("Max-Age");
        assertThat(refreshDeCookie(rotada)).isNotEqualTo(sesion);

        MvcResult rotadaPersistente = refrescar(persistente, "http://localhost:4200")
                .andExpect(status().isOk()).andReturn();
        assertThat(rotadaPersistente.getResponse().getHeader("Set-Cookie")).contains("Max-Age=604800");
    }

    @Test
    void reutilizarUnRefreshYaRotadoRevocaTodaLaFamilia() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String original = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, false).andReturn());
        String nuevo = refreshDeCookie(refrescar(original, null).andExpect(status().isOk()).andReturn());

        // Alguien reutiliza el token ya rotado: se revoca la familia...
        refrescar(original, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        // ...y el token nuevo, aunque no se había usado, deja de valer
        refrescar(nuevo, null).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSinCookieOConOrigenAjenoSeRechaza() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String refresh = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, false).andReturn());

        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        // Otro sitio: lo rechaza antes el filtro CORS de Spring (403 sin cuerpo JSON)
        refrescar(refresh, "https://atacante.example").andExpect(status().isForbidden());
        // Mismo host que la API (CORS no interviene) pero distinto del frontend: lo rechaza el servicio
        refrescar(refresh, "http://localhost")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // El rechazo por origen no ha gastado el token
        refrescar(refresh, "http://localhost:4200").andExpect(status().isOk());
    }

    @Test
    void logoutBorraLaCookieYRevocaElRefresh() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String refresh = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, true).andReturn());

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(CookieRefresh.NOMBRE, refresh)))
                .andExpect(status().isNoContent()).andReturn();

        assertThat(logout.getResponse().getHeader("Set-Cookie"))
                .startsWith(CookieRefresh.NOMBRE + "=;").contains("Max-Age=0", "Path=/api/v1/auth");
        refrescar(refresh, null).andExpect(status().isUnauthorized());
        // Sin cookie también responde 204
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void logoutTodasExigeTokenYCierraTodasLasSesiones() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        MvcResult primera = login("ana@ejemplo.com", PASSWORD, false).andReturn();
        String refreshUno = refreshDeCookie(primera);
        String refreshDos = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, false).andReturn());
        String accessToken = JsonPath.read(primera.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/auth/logout-todas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout-todas").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        refrescar(refreshUno, null).andExpect(status().isUnauthorized());
        refrescar(refreshDos, null).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------
    // Recuperación de contraseña
    // ---------------------------------------------------------------------------------------------

    @Test
    void recuperarYRestablecerCambiaLaContrasenaYCierraLasSesiones() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);
        String refreshAntiguo = refreshDeCookie(login("ana@ejemplo.com", PASSWORD, false).andReturn());

        recuperar("ANA@ejemplo.com").andExpect(status().isAccepted()).andExpect(jsonPath("$.mensaje").isNotEmpty());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarRecuperacion(eq("ana@ejemplo.com"), eq("Ana"), token.capture());

        restablecer(token.getValue(), "Nueva-clave-segura-7").andExpect(status().isNoContent());

        login("ana@ejemplo.com", PASSWORD, false).andExpect(status().isUnauthorized());
        login("ana@ejemplo.com", "Nueva-clave-segura-7", false).andExpect(status().isOk());
        refrescar(refreshAntiguo, null).andExpect(status().isUnauthorized());
        restablecer(token.getValue(), "Otra-clave-segura-8")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TOKEN_NO_VALIDO"));
    }

    @Test
    void recuperarNoRevelaSiLaCuentaExisteYValidaLaNuevaContrasena() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);

        String existente = recuperar("ana@ejemplo.com").andReturn().getResponse().getContentAsString();
        Mockito.reset(correoServicio);
        String inexistente = recuperar("nadie@ejemplo.com")
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(existente);
        verify(correoServicio, never()).enviarRecuperacion(anyString(), anyString(), anyString());
        restablecer("cualquiera", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.campo == 'nuevaPassword')]").exists());
    }

    // ---------------------------------------------------------------------------------------------
    // Límite de intentos
    // ---------------------------------------------------------------------------------------------

    @Test
    void cincoLoginsFallidosBloqueanElEmailSinCambiarElEstadoDeLaCuenta() throws Exception {
        registrar("ana@ejemplo.com", PASSWORD, true, true);

        for (int i = 0; i < 5; i++) {
            login("ana@ejemplo.com", "Contrasena-incorrecta-" + i, false).andExpect(status().isUnauthorized());
        }

        // Ni siquiera con la contraseña correcta (y desde otra IP) hasta que se recupere un intento
        login("ana@ejemplo.com", PASSWORD, false)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("17\\d|180")))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
        assertThat(usuarioRepositorio.findByEmail("ana@ejemplo.com").orElseThrow().getEstado())
                .isEqualTo(EstadoUsuario.ACTIVO);
    }

    @Test
    void cincoLoginsFallidosDesdeUnaIpLaBloquean() throws Exception {
        String ip = ipAleatoria();
        for (int i = 0; i < 5; i++) {
            login("victima" + i + "@ejemplo.com", PASSWORD, false, ip).andExpect(status().isUnauthorized());
        }

        login("otra@ejemplo.com", PASSWORD, false, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void registroYRecuperacionSeLimitanA3PorHoraYPorIp() throws Exception {
        String ip = ipAleatoria();
        for (int i = 0; i < 3; i++) {
            registrar("usuario" + i + "@ejemplo.com", PASSWORD, true, true, ip).andExpect(status().isAccepted());
        }
        registrar("usuario3@ejemplo.com", PASSWORD, true, true, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(usuarioRepositorio.count()).isEqualTo(3);

        for (int i = 0; i < 3; i++) {
            recuperar("usuario0@ejemplo.com", ip).andExpect(status().isAccepted());
        }
        recuperar("usuario0@ejemplo.com", ip).andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades (cada petición usa una IP aleatoria para que los límites no se mezclen entre tests)
    // ---------------------------------------------------------------------------------------------

    private ResultActions registrar(String email, String password, boolean mayorDeEdad, boolean consentimientoSalud)
            throws Exception {
        return registrar(email, password, mayorDeEdad, consentimientoSalud, ipAleatoria());
    }

    private ResultActions registrar(String email, String password, boolean mayorDeEdad, boolean consentimientoSalud,
            String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/registro").with(desdeIp(ip))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":" Ana ","email":"%s","password":"%s",
                 "mayorDeEdad":%s,"aceptaTerminos":true,"consentimientoSalud":%s}
                """.formatted(email, password, mayorDeEdad, consentimientoSalud)));
    }

    private ResultActions verificar(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verificar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private ResultActions reenviar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reenviar-verificacion").with(desdeIp(ipAleatoria()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions login(String email, String password, boolean mantenerSesion) throws Exception {
        return login(email, password, mantenerSesion, ipAleatoria());
    }

    private ResultActions login(String email, String password, boolean mantenerSesion, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(desdeIp(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"mantenerSesion\":%s}"
                        .formatted(email, password, mantenerSesion)));
    }

    private ResultActions refrescar(String refreshToken, String origen) throws Exception {
        MockHttpServletRequestBuilder peticion = post("/api/v1/auth/refresh")
                .cookie(new Cookie(CookieRefresh.NOMBRE, refreshToken));
        if (origen != null) {
            peticion.header("Origin", origen);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions recuperar(String email) throws Exception {
        return recuperar(email, ipAleatoria());
    }

    private ResultActions recuperar(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperar-password").with(desdeIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions restablecer(String token, String nuevaPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/restablecer-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"nuevaPassword\":\"%s\"}".formatted(token, nuevaPassword)));
    }

    /** Valor de la cookie ns_refresh de la respuesta. */
    private static String refreshDeCookie(MvcResult resultado) {
        String cookie = resultado.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).startsWith(CookieRefresh.NOMBRE + "=");
        return cookie.substring(CookieRefresh.NOMBRE.length() + 1, cookie.indexOf(';'));
    }

    private static RequestPostProcessor desdeIp(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private static String ipAleatoria() {
        ThreadLocalRandom aleatorio = ThreadLocalRandom.current();
        return "10." + aleatorio.nextInt(256) + "." + aleatorio.nextInt(256) + "." + aleatorio.nextInt(1, 255);
    }

    /** Captura el token que se pasó al servicio de correo en el email de verificación. */
    private String tokenEnviado(String email) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarVerificacion(eq(email), eq("Ana"), token.capture());
        return token.getValue();
    }
}
