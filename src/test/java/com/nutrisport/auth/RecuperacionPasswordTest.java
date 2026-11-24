package com.nutrisport.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recuperación de contraseña por email (§3.A, HU-03). */
@AutoConfigureMockMvc
class RecuperacionPasswordTest extends IntegracionBase {

    private static final String EMAIL = "ana@ejemplo.com";
    private static final String PASSWORD_ANTIGUA = "Avena-y-platano-42";
    private static final String PASSWORD_NUEVA = "Nueva-clave-segura-7";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final LimitadorPeticiones limitador;

    @MockitoBean
    private CorreoServicio correoServicio;

    @Autowired
    RecuperacionPasswordTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder,
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
    void conEmailInexistenteResponde202SinEnviarCorreo() throws Exception {
        solicitar("nadie@ejemplo.com").andExpect(status().isAccepted());

        verify(correoServicio, never()).enviarRecuperacion(anyString(), anyString(), anyString());
    }

    @Test
    void conEmailExistenteEnviaElCorreoYPermiteCambiarLaContrasena() throws Exception {
        solicitar(EMAIL).andExpect(status().isAccepted());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correoServicio).enviarRecuperacion(eq(EMAIL), eq("Ana"), token.capture());

        mockMvc.perform(post("/api/v1/auth/restablecer-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"nuevaPassword\":\"%s\"}".formatted(token.getValue(), PASSWORD_NUEVA)))
                .andExpect(status().isNoContent());

        login(PASSWORD_NUEVA).andExpect(status().isOk());
        login(PASSWORD_ANTIGUA).andExpect(status().isUnauthorized());
    }

    private ResultActions solicitar(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperar-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"mantenerSesion\":false}".formatted(EMAIL, password)));
    }

    private void crearUsuario() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD_ANTIGUA));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("2026-11");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuarioRepositorio.save(usuario);
    }
}
