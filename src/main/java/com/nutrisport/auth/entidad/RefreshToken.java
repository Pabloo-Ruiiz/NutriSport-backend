package com.nutrisport.auth.entidad;

import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh token rotatorio (tabla {@code refresh_token}, §5.5). Solo se guarda el SHA-256 del token
 * en hexadecimal. Todos los tokens nacidos del mismo inicio de sesión comparten {@code familia}.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** SHA-256 en hexadecimal (64 caracteres); la columna es char(64). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "familia", nullable = false)
    private UUID familia;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "revocado", nullable = false)
    private boolean revocado;

    /** Id del token que sustituyó a este al rotar; nulo mientras no se ha usado. */
    @Column(name = "reemplazado_por")
    private UUID reemplazadoPor;

    public RefreshToken() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getFamilia() {
        return familia;
    }

    public void setFamilia(UUID familia) {
        this.familia = familia;
    }

    public OffsetDateTime getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(OffsetDateTime expiraEn) {
        this.expiraEn = expiraEn;
    }

    public boolean isRevocado() {
        return revocado;
    }

    public void setRevocado(boolean revocado) {
        this.revocado = revocado;
    }

    public UUID getReemplazadoPor() {
        return reemplazadoPor;
    }

    public void setReemplazadoPor(UUID reemplazadoPor) {
        this.reemplazadoPor = reemplazadoPor;
    }
}
