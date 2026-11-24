package com.nutrisport.perfil.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Restricción o preferencia alimentaria del usuario (tabla {@code preferencia_alimentaria}, §3.B y
 * §6.1). Según el {@link TipoPreferencia} se rellena un campo u otro (CHECK
 * ck_preferencia_alimentaria_coherencia de V3): {@code ALERGIA} → alérgeno, {@code INTOLERANCIA} →
 * intolerancia, {@code DIETA} → dieta y {@code RECHAZO}/{@code FAVORITO} → alimento o categoría.
 */
@Entity
@Table(name = "preferencia_alimentaria")
public class PreferenciaAlimentaria extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoPreferencia tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "alergeno", length = 20)
    private Alergeno alergeno;

    @Enumerated(EnumType.STRING)
    @Column(name = "intolerancia", length = 10)
    private Intolerancia intolerancia;

    @Enumerated(EnumType.STRING)
    @Column(name = "dieta", length = 15)
    private Dieta dieta;

    /** Id del alimento; pasará a ser relación cuando exista la tabla alimento. */
    @Column(name = "alimento_id")
    private UUID alimentoId;

    /** Id de la categoría; pasará a ser relación cuando exista la tabla categoria. */
    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", nullable = false, length = 12)
    private Severidad severidad;

    public PreferenciaAlimentaria() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public TipoPreferencia getTipo() {
        return tipo;
    }

    public void setTipo(TipoPreferencia tipo) {
        this.tipo = tipo;
    }

    public Alergeno getAlergeno() {
        return alergeno;
    }

    public void setAlergeno(Alergeno alergeno) {
        this.alergeno = alergeno;
    }

    public Intolerancia getIntolerancia() {
        return intolerancia;
    }

    public void setIntolerancia(Intolerancia intolerancia) {
        this.intolerancia = intolerancia;
    }

    public Dieta getDieta() {
        return dieta;
    }

    public void setDieta(Dieta dieta) {
        this.dieta = dieta;
    }

    public UUID getAlimentoId() {
        return alimentoId;
    }

    public void setAlimentoId(UUID alimentoId) {
        this.alimentoId = alimentoId;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public void setCategoriaId(UUID categoriaId) {
        this.categoriaId = categoriaId;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public void setSeveridad(Severidad severidad) {
        this.severidad = severidad;
    }
}
