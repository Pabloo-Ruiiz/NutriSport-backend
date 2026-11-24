package com.nutrisport.auth.servicio;

import com.nutrisport.auth.entidad.RefreshToken;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.RefreshTokenRepositorio;
import com.nutrisport.comun.config.JwtProperties;
import com.nutrisport.comun.util.Tokens;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens opacos y rotatorios (§5.5). Solo se guarda su SHA-256.
 * <p>
 * Cada login abre una familia; cada rotación crea un token nuevo de la misma familia y marca el
 * anterior como reemplazado. Si llega un token ya rotado o revocado, alguien lo ha copiado: se
 * revoca toda la familia y tanto el atacante como el usuario legítimo tienen que volver a entrar.
 */
@Service
@Transactional
public class RefreshTokenServicio {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenServicio.class);

    static final String SESION_NO_VALIDA = "La sesión no es válida o ha caducado";

    /** Resultado de una rotación: el usuario y el refresh token nuevo en claro. */
    public record Rotacion(Usuario usuario, String token, boolean persistente) {
    }

    private final RefreshTokenRepositorio refreshTokenRepositorio;
    private final JwtProperties jwtProperties;

    public RefreshTokenServicio(RefreshTokenRepositorio refreshTokenRepositorio, JwtProperties jwtProperties) {
        this.refreshTokenRepositorio = refreshTokenRepositorio;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Guarda un refresh token nuevo y lo devuelve en claro (para la cookie).
     *
     * @param familia     familia del token rotado, o {@code null} para empezar una familia nueva (login)
     * @param persistente "Mantener la sesión iniciada"
     */
    public String emitir(Usuario usuario, UUID familia, boolean persistente) {
        String token = Tokens.generar();
        guardar(usuario, token, familia != null ? familia : UUID.randomUUID(), persistente);
        return token;
    }

    /**
     * Sustituye un refresh token válido por uno nuevo de la misma familia.
     * <p>
     * {@code noRollbackFor}: la revocación de la familia al detectar una reutilización tiene que
     * guardarse aunque el método termine con {@link BadCredentialsException}.
     *
     * @throws BadCredentialsException si el token no existe, está revocado, ya se rotó (se revoca la
     *                                 familia), ha caducado o su usuario no está activo
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Rotacion rotar(String tokenPlano) {
        RefreshToken actual = buscar(tokenPlano)
                .orElseThrow(() -> new BadCredentialsException(SESION_NO_VALIDA));

        if (actual.isRevocado() || actual.getReemplazadoPor() != null) {
            // Sin el token ni el usuario en el log: solo la familia afectada
            LOG.warn("Reutilización de un refresh token: se revoca la familia {}", actual.getFamilia());
            refreshTokenRepositorio.revocarFamilia(actual.getFamilia());
            throw new BadCredentialsException(SESION_NO_VALIDA);
        }
        if (!actual.getExpiraEn().toInstant().isAfter(Instant.now())) {
            throw new BadCredentialsException(SESION_NO_VALIDA);
        }
        Usuario usuario = actual.getUsuario();
        if (!usuario.estaActivo()) {
            throw new BadCredentialsException(SESION_NO_VALIDA);
        }

        String tokenNuevo = Tokens.generar();
        RefreshToken nuevo = guardar(usuario, tokenNuevo, actual.getFamilia(), actual.isPersistente());
        actual.setReemplazadoPor(nuevo.getId());
        return new Rotacion(usuario, tokenNuevo, actual.isPersistente());
    }

    /** Cierra la sesión de este token: revoca toda su familia. Un token desconocido se ignora. */
    public void revocar(String tokenPlano) {
        buscar(tokenPlano).ifPresent(token -> refreshTokenRepositorio.revocarFamilia(token.getFamilia()));
    }

    /** Cierra todas las sesiones del usuario. */
    public void revocarTodos(UUID usuarioId) {
        refreshTokenRepositorio.revocarTodosDeUsuario(usuarioId);
    }

    private Optional<RefreshToken> buscar(String tokenPlano) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepositorio.buscarParaRotar(Tokens.sha256Hex(tokenPlano.trim()));
    }

    private RefreshToken guardar(Usuario usuario, String token, UUID familia, boolean persistente) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setTokenHash(Tokens.sha256Hex(token));
        refreshToken.setFamilia(familia);
        refreshToken.setPersistente(persistente);
        refreshToken.setExpiraEn(OffsetDateTime.now(ZoneOffset.UTC).plusDays(jwtProperties.refreshDias()));
        return refreshTokenRepositorio.save(refreshToken);
    }
}
