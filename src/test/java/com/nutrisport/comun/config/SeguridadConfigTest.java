package com.nutrisport.comun.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Comprueba la cadena de seguridad completa: rutas públicas, 401/403, roles, CORS y cabeceras. */
@AutoConfigureMockMvc
@Transactional
class SeguridadConfigTest extends IntegracionBase {

    private static final String FRONTEND = "http://localhost:4200";

    private final MockMvc mockMvc;
    private final UsuarioRepositorio usuarioRepositorio;
    private final JwtServicio jwtServicio;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    SeguridadConfigTest(MockMvc mockMvc, UsuarioRepositorio usuarioRepositorio, JwtServicio jwtServicio,
            PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.usuarioRepositorio = usuarioRepositorio;
        this.jwtServicio = jwtServicio;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void rutaProtegidaSinTokenDevuelve401ProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/cuenta"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.instance").value("/api/v1/cuenta"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void tokenNoValidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/cuenta").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void rutasPublicasNoPidenToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // 400 de validación (no 401): la seguridad deja pasar la petición hasta el controlador
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        // Solo POST es público en /auth
        mockMvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void deportistaNoEntraEnAdminPeroAdminSi() throws Exception {
        String tokenDeportista = token(guardarUsuario("deportista@ejemplo.com", Rol.DEPORTISTA, EstadoUsuario.ACTIVO));
        String tokenAdmin = token(guardarUsuario("admin@ejemplo.com", Rol.ADMIN, EstadoUsuario.ACTIVO));

        mockMvc.perform(get("/api/v1/admin/usuarios").header("Authorization", "Bearer " + tokenDeportista))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // El administrador pasa la autorización; 404 porque aún no existe el controlador
        mockMvc.perform(get("/api/v1/admin/usuarios").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
        // Un deportista autenticado pasa la autorización de las rutas normales
        mockMvc.perform(get("/api/v1/cuenta").header("Authorization", "Bearer " + tokenDeportista))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioBloqueadoConTokenVigenteDevuelve401() throws Exception {
        Usuario usuario = guardarUsuario("bloqueado@ejemplo.com", Rol.DEPORTISTA, EstadoUsuario.ACTIVO);
        String token = token(usuario);
        usuario.setEstado(EstadoUsuario.BLOQUEADO);
        usuarioRepositorio.saveAndFlush(usuario);

        mockMvc.perform(get("/api/v1/cuenta").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsSoloPermiteElFrontend() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://atacante.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anadeLasCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void passwordEncoderEsBcryptConCoste12() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(passwordEncoder.encode("una-contraseña-larga")).startsWith("$2a$12$");
    }

    private Usuario guardarUsuario(String email, Rol rol, EstadoUsuario estado) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("$2a$12$hashDePruebaQueNoEsUnaContrasenaReal000000000000000000");
        usuario.setNombre("Prueba");
        usuario.setEstado(estado);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("1.0");
        usuario.anadirRol(rol);
        return usuarioRepositorio.saveAndFlush(usuario);
    }

    private String token(Usuario usuario) {
        return jwtServicio.generarAccessToken(UsuarioAutenticado.de(usuario));
    }
}
