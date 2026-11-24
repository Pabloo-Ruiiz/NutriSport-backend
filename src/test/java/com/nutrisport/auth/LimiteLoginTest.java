package com.nutrisport.auth;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.limite.LimitadorPeticiones;
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
import org.springframework.test.web.servlet.ResultActions;

/** Límite de 5 logins fallidos cada 15 minutos (§8.1). */
@AutoConfigureMockMvc
class LimiteLoginTest extends IntegracionBase {

    private static final String EMAIL = "ana@ejemplo.com";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final LimitadorPeticiones limitador;

    @MockitoBean
    private CorreoServicio correoServicio;

    @Autowired
    LimiteLoginTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder,
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
        // El limitador vive en memoria y lo comparten todos los tests del contexto
        limitador.reiniciarTodo();
        crearUsuario();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    @Test
    void cincoFallosDevuelven401YElSextoIntento429ConRetryAfter() throws Exception {
        for (int intento = 1; intento <= 5; intento++) {
            loginConContrasenaIncorrecta()
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }

        loginConContrasenaIncorrecta()
                .andExpect(status().isTooManyRequests())
                // Segundos hasta el siguiente intento: entero positivo (unos 180 con 5 cada 15 min)
                .andExpect(header().string("Retry-After", matchesPattern("[1-9]\\d*")))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
    }

    private ResultActions loginConContrasenaIncorrecta() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Contrasena-incorrecta-1\",\"mantenerSesion\":false}"
                        .formatted(EMAIL)));
    }

    private void crearUsuario() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode("Avena-y-platano-42"));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("2026-11");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuarioRepositorio.save(usuario);
    }
}
