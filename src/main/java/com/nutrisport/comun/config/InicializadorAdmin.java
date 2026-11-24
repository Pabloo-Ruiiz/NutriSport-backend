package com.nutrisport.comun.config;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el administrador de desarrollo (perfil {@code local}) con {@code ADMIN_EMAIL} y
 * {@code ADMIN_PASS} del {@code .env}, si aún no existe. Nunca escribe la contraseña en el log.
 */
@Component
@Profile("local")
public class InicializadorAdmin implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorAdmin.class);

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String versionConsentimiento;

    public InicializadorAdmin(UsuarioRepositorio usuarioRepositorio, PasswordEncoder passwordEncoder,
            @Value("${ADMIN_EMAIL:}") String email, @Value("${ADMIN_PASS:}") String password,
            @Value("${app.consentimiento.version}") String versionConsentimiento) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
        this.versionConsentimiento = versionConsentimiento;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (email.isBlank() || password == null || password.isBlank()) {
            LOG.warn("No se crea el administrador: faltan ADMIN_EMAIL o ADMIN_PASS en el .env");
            return;
        }
        if (usuarioRepositorio.existsByEmail(email)) {
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setEstado(EstadoUsuario.ACTIVO);
        admin.setEmailVerificadoEn(ahora);
        admin.setMayorEdadDeclaradaEn(ahora);
        admin.setConsentimientoSaludEn(ahora);
        admin.setConsentimientoSaludVersion(versionConsentimiento);
        admin.anadirRol(Rol.ADMIN);
        admin.anadirRol(Rol.DEPORTISTA);
        usuarioRepositorio.save(admin);
        LOG.info("Administrador de desarrollo creado con el email de ADMIN_EMAIL");
    }
}
