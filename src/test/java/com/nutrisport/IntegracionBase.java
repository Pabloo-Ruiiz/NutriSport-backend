package com.nutrisport;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integración: arranca el contexto completo de Spring con el perfil "test"
 * contra un PostgreSQL 16 real en Docker.
 * <p>
 * El contenedor sigue el patrón singleton: se arranca una sola vez en el bloque estático y lo
 * comparten todas las clases de test que extienden esta base. No se para de forma explícita;
 * Testcontainers (Ryuk) lo elimina al terminar la JVM. Al tener todas las clases la misma
 * configuración, Spring reutiliza también el contexto en caché.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegracionBase {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Hash BCrypt de relleno: los usuarios de {@link #crearUsuarioYToken()} se autentican con el token
     * y nunca hacen login, así que no se paga el coste de BCrypt en cada test.
     */
    private static final String HASH_DE_RELLENO = "$2a$12$" + "x".repeat(53);

    private UsuarioRepositorio usuarioRepositorioBase;
    private JwtServicio jwtServicioBase;

    /**
     * Recibe las dependencias de los métodos auxiliares por parámetro (SpringExtension solo resuelve
     * los parámetros de un método de ciclo de vida si llevan {@code @Autowired}),
     * para no obligar a cada subclase a pasarlas por su constructor. Se ejecuta antes que los
     * {@code @BeforeEach} de las subclases.
     */
    @BeforeEach
    void prepararAuxiliaresBase(@Autowired UsuarioRepositorio usuarioRepositorio,
            @Autowired JwtServicio jwtServicio) {
        this.usuarioRepositorioBase = usuarioRepositorio;
        this.jwtServicioBase = jwtServicio;
    }

    /**
     * Crea un deportista activo, con el email verificado y el consentimiento de salud vigente, y le
     * emite un access token. El email es único en cada llamada, así que puede usarse varias veces en
     * un mismo test (por ejemplo, para comprobar que un usuario no ve los recursos de otro).
     */
    protected UsuarioConToken crearUsuarioYToken() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setNombre("Deportista de prueba");
        usuario.setEmail("deportista-" + UUID.randomUUID() + "@ejemplo.com");
        usuario.setPasswordHash(HASH_DE_RELLENO);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setEmailVerificadoEn(ahora);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("2026-11");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuario = usuarioRepositorioBase.save(usuario);
        String token = jwtServicioBase.generarAccessToken(UsuarioAutenticado.de(usuario));
        return new UsuarioConToken(usuario, token);
    }

    /**
     * Usuario creado para un test y su access token.
     *
     * @param usuario entidad ya guardada
     * @param token   access token JWT sin el prefijo "Bearer "
     */
    protected record UsuarioConToken(Usuario usuario, String token) {

        /** Valor de la cabecera {@code Authorization}. */
        public String cabeceraAutorizacion() {
            return "Bearer " + token;
        }
    }
}
