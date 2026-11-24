package com.nutrisport.auth.servicio;

import com.nutrisport.auth.entidad.RefreshToken;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.RefreshTokenRepositorio;
import com.nutrisport.comun.config.JwtProperties;
import com.nutrisport.comun.util.Tokens;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emisión de refresh tokens opacos (§5.5). Solo se guarda su SHA-256. */
@Service
@Transactional
public class RefreshTokenServicio {

    private final RefreshTokenRepositorio refreshTokenRepositorio;
    private final JwtProperties jwtProperties;

    public RefreshTokenServicio(RefreshTokenRepositorio refreshTokenRepositorio, JwtProperties jwtProperties) {
        this.refreshTokenRepositorio = refreshTokenRepositorio;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Guarda un refresh token nuevo y lo devuelve en claro (para la cookie).
     *
     * @param familia familia del token rotado, o {@code null} para empezar una familia nueva (login)
     */
    public String emitir(Usuario usuario, UUID familia) {
        String token = Tokens.generar();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setTokenHash(Tokens.sha256Hex(token));
        refreshToken.setFamilia(familia != null ? familia : UUID.randomUUID());
        refreshToken.setExpiraEn(OffsetDateTime.now(ZoneOffset.UTC).plusDays(jwtProperties.refreshDias()));
        refreshTokenRepositorio.save(refreshToken);
        return token;
    }
}
