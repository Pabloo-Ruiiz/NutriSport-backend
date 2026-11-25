package com.nutrisport.recetas.entidad;

import com.nutrisport.auth.entidad.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Receta marcada como favorita por un usuario (tabla {@code receta_favorita}, §6.1). Clave compuesta
 * (usuario, receta) y solo fecha de creación: no extiende {@code EntidadBase}.
 */
@Entity
@Table(name = "receta_favorita")
public class RecetaFavorita {

    /** Clave compuesta (usuario_id, receta_id). */
    @Embeddable
    public record Id(
            @Column(name = "usuario_id", nullable = false) UUID usuarioId,
            @Column(name = "receta_id", nullable = false) UUID recetaId) implements Serializable {
    }

    @EmbeddedId
    private Id id;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @MapsId("recetaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id")
    private Receta receta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected RecetaFavorita() {
    }

    public RecetaFavorita(Usuario usuario, Receta receta) {
        this.id = new Id(usuario.getId(), receta.getId());
        this.usuario = usuario;
        this.receta = receta;
    }

    @PrePersist
    protected void alCrear() {
        this.creadoEn = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public Id getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Receta getReceta() {
        return receta;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }
}
