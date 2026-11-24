package com.nutrisport.auth.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.RefreshToken;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.TokenUsuario;
import com.nutrisport.auth.entidad.Unidades;
import com.nutrisport.auth.entidad.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Comprueba el mapeo de las entidades de auth contra la V1 y las consultas de sus repositorios. */
@Transactional
class RepositoriosAuthTest extends IntegracionBase {

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final TokenUsuarioRepositorio tokensUsuario;
    private final EntityManager em;

    @Autowired
    RepositoriosAuthTest(UsuarioRepositorio usuarios, RefreshTokenRepositorio refreshTokens,
            TokenUsuarioRepositorio tokensUsuario, EntityManager em) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.tokensUsuario = tokensUsuario;
        this.em = em;
    }

    @Test
    void guardaUsuarioConRolesYLoBuscaPorEmail() {
        Usuario usuario = nuevoUsuario("ana@ejemplo.com");
        usuario.anadirRol(Rol.DEPORTISTA);
        usuario.anadirRol(Rol.ADMIN);
        usuarios.saveAndFlush(usuario);
        em.clear();

        Usuario leido = usuarios.findByEmail("ana@ejemplo.com").orElseThrow();
        assertThat(leido.getId()).isNotNull();
        assertThat(leido.getCreadoEn()).isNotNull();
        assertThat(leido.getRoles()).containsExactlyInAnyOrder(Rol.DEPORTISTA, Rol.ADMIN);
        assertThat(leido.tieneRol(Rol.ADMIN)).isTrue();
        assertThat(leido.estaActivo()).isTrue();
        assertThat(leido.emailVerificado()).isFalse();
        assertThat(leido.consentimientoVigente()).isTrue();
        assertThat(leido.getUnidades()).isEqualTo(Unidades.METRICO);
        assertThat(usuarios.existsByEmail("ana@ejemplo.com")).isTrue();
        assertThat(usuarios.existsByEmail("otro@ejemplo.com")).isFalse();
    }

    @Test
    void revocaTokensPorUsuarioYPorFamilia() {
        Usuario usuario = usuarios.save(nuevoUsuario("luis@ejemplo.com"));
        UUID familiaA = UUID.randomUUID();
        UUID familiaB = UUID.randomUUID();
        refreshTokens.save(nuevoRefresh(usuario, "a1", familiaA));
        refreshTokens.save(nuevoRefresh(usuario, "a2", familiaA));
        refreshTokens.save(nuevoRefresh(usuario, "b1", familiaB));

        assertThat(refreshTokens.revocarFamilia(familiaA)).isEqualTo(2);
        assertThat(refreshTokens.findByFamilia(familiaA)).allMatch(RefreshToken::isRevocado);
        assertThat(refreshTokens.findByTokenHash(hash("b1")).orElseThrow().isRevocado()).isFalse();

        assertThat(refreshTokens.revocarTodosDeUsuario(usuario.getId())).isEqualTo(1);
        assertThat(refreshTokens.findByTokenHash(hash("b1")).orElseThrow().isRevocado()).isTrue();
    }

    @Test
    void invalidaSoloLosTokensPendientesDelTipo() {
        Usuario usuario = usuarios.save(nuevoUsuario("eva@ejemplo.com"));
        OffsetDateTime enUnaHora = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        tokensUsuario.save(nuevoToken(usuario, TipoToken.VERIFICACION_EMAIL, "v1", enUnaHora));
        tokensUsuario.save(nuevoToken(usuario, TipoToken.RECUPERACION_PASSWORD, "r1", enUnaHora));

        assertThat(tokensUsuario.invalidarPendientes(usuario.getId(), TipoToken.VERIFICACION_EMAIL)).isEqualTo(1);

        Instant ahora = Instant.now();
        TokenUsuario verificacion = tokensUsuario.findByTokenHashAndTipo(hash("v1"), TipoToken.VERIFICACION_EMAIL)
                .orElseThrow();
        TokenUsuario recuperacion = tokensUsuario.findByTokenHashAndTipo(hash("r1"), TipoToken.RECUPERACION_PASSWORD)
                .orElseThrow();
        assertThat(verificacion.estaVigente(ahora)).isFalse();
        assertThat(verificacion.getUsadoEn()).isNull();
        assertThat(recuperacion.estaVigente(ahora)).isTrue();
        assertThat(tokensUsuario.findByTokenHashAndTipo(hash("v1"), TipoToken.CAMBIO_EMAIL)).isEmpty();
    }

    private static Usuario nuevoUsuario(String email) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash("$2a$12$hashDePruebaQueNoEsUnaContrasenaReal000000000000000000");
        usuario.setNombre("Prueba");
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion("1.0");
        return usuario;
    }

    private static RefreshToken nuevoRefresh(Usuario usuario, String semilla, UUID familia) {
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setTokenHash(hash(semilla));
        token.setFamilia(familia);
        token.setExpiraEn(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        return token;
    }

    private static TokenUsuario nuevoToken(Usuario usuario, TipoToken tipo, String semilla, OffsetDateTime expira) {
        TokenUsuario token = new TokenUsuario();
        token.setUsuario(usuario);
        token.setTipo(tipo);
        token.setTokenHash(hash(semilla));
        token.setExpiraEn(expira);
        return token;
    }

    /** Hash ficticio de 64 caracteres hexadecimales a partir de una semilla corta. */
    private static String hash(String semilla) {
        String hex = String.format("%x", new java.math.BigInteger(1, semilla.getBytes()));
        return (hex + "0".repeat(64)).substring(0, 64);
    }
}
