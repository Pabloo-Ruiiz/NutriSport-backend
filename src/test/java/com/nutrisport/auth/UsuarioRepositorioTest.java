package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Comprueba el mapeo de {@link Usuario} contra la migración V1 (roles, auditoría y email único). */
@Transactional
class UsuarioRepositorioTest extends IntegracionBase {

    private final UsuarioRepositorio usuarioRepositorio;
    private final EntityManager entityManager;

    @Autowired
    UsuarioRepositorioTest(UsuarioRepositorio usuarioRepositorio, EntityManager entityManager) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.entityManager = entityManager;
    }

    @Test
    void guardaUsuarioConRolesYLoRecuperaPorEmail() {
        Usuario usuario = nuevoUsuario("marta@ejemplo.com");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuario.anadirRol(Rol.ADMIN);
        usuarioRepositorio.saveAndFlush(usuario);
        // Vacía la caché de primer nivel para leer de verdad de la base de datos
        entityManager.clear();

        Usuario recuperado = usuarioRepositorio.findByEmail("marta@ejemplo.com").orElseThrow();

        assertThat(recuperado.getId()).isEqualTo(usuario.getId());
        assertThat(recuperado.getRoles()).containsExactlyInAnyOrder(Rol.DEPORTISTA, Rol.ADMIN);
        assertThat(recuperado.tieneRol(Rol.ADMIN)).isTrue();
    }

    @Test
    void rellenaCreadoEnAlGuardar() {
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        Usuario guardado = usuarioRepositorio.saveAndFlush(nuevoUsuario("pablo@ejemplo.com"));

        assertThat(guardado.getCreadoEn()).isNotNull().isAfter(antes);
        assertThat(guardado.getActualizadoEn()).isEqualTo(guardado.getCreadoEn());
    }

    @Test
    void noPermiteDosUsuariosConElMismoEmail() {
        usuarioRepositorio.saveAndFlush(nuevoUsuario("repetido@ejemplo.com"));

        Usuario duplicado = nuevoUsuario("repetido@ejemplo.com");

        assertThatThrownBy(() -> usuarioRepositorio.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Usuario nuevoUsuario(String email) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("$2a$12$hashDePruebaQueNoEsUnaContrasenaReal000000000000000000");
        usuario.setNombre("Usuario de prueba");
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("1.0");
        return usuario;
    }
}
