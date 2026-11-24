package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.web.CookieRefresh;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Rotación del refresh token, detección de reutilización y logout (§5.5). */
@AutoConfigureMockMvc
class RefreshTokenTest extends IntegracionBase {

    private static final String EMAIL = "ana@ejemplo.com";
    private static final String PASSWORD = "Avena-y-platano-42";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final LimitadorPeticiones limitador;

    @MockitoBean
    private CorreoServicio correoServicio;

    @Autowired
    RefreshTokenTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc, LimitadorPeticiones limitador) {
        this.mockMvc = mockMvc;
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.limitador = limitador;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        limitador.reiniciarTodo();
        crearUsuario();
    }

    /** Las peticiones confirman sus transacciones: se borran los datos para no afectar a otros tests. */
    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    @Test
    void refreshDevuelveOtroAccessTokenYOtraCookie() throws Exception {
        MvcResult login = login().andExpect(status().isOk()).andReturn();
        String accessLogin = accessToken(login);
        String cookieLogin = cookieRefresh(login);

        MvcResult refresco = refrescar(cookieLogin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        assertThat(accessToken(refresco)).isNotEqualTo(accessLogin);
        assertThat(cookieRefresh(refresco)).isNotEqualTo(cookieLogin);
    }

    @Test
    void reutilizarLaCookieAntiguaDevuelve401YRevocaLaFamilia() throws Exception {
        String cookieAntigua = cookieRefresh(login().andExpect(status().isOk()).andReturn());
        String cookieNueva = cookieRefresh(refrescar(cookieAntigua).andExpect(status().isOk()).andReturn());

        refrescar(cookieAntigua)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        // La familia entera está revocada: la cookie nueva, sin usar todavía, tampoco vale
        refrescar(cookieNueva).andExpect(status().isUnauthorized());
    }

    @Test
    void despuesDelLogoutElRefreshDevuelve401() throws Exception {
        String cookie = cookieRefresh(login().andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(CookieRefresh.NOMBRE, cookie)))
                .andExpect(status().isNoContent());

        refrescar(cookie).andExpect(status().isUnauthorized());
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"mantenerSesion\":false}".formatted(EMAIL, PASSWORD)));
    }

    private ResultActions refrescar(String cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(CookieRefresh.NOMBRE, cookie)));
    }

    private static String accessToken(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String cookieRefresh(MvcResult resultado) {
        String cabecera = resultado.getResponse().getHeader("Set-Cookie");
        assertThat(cabecera).startsWith(CookieRefresh.NOMBRE + "=");
        return cabecera.substring(CookieRefresh.NOMBRE.length() + 1, cabecera.indexOf(';'));
    }

    private void crearUsuario() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("2026-11");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuarioRepositorio.save(usuario);
    }
}
