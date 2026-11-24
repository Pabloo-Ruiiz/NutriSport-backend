package com.nutrisport.actividad.entidad;

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
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entrenamiento planificado o realizado (tabla {@code entrenamiento}, §3.G y §6.1). {@code kcalEstimadas}
 * es el gasto <em>neto</em> calculado con el MET de la actividad; solo los PLANIFICADO y REALIZADO
 * cuentan para el objetivo diario.
 */
@Entity
@Table(name = "entrenamiento")
public class Entrenamiento extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_actividad_codigo", nullable = false)
    private TipoActividad tipoActividad;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    /** Entre 1 y 600 minutos. */
    @Column(name = "duracion_min", nullable = false)
    private short duracionMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "intensidad", nullable = false, length = 5)
    private Intensidad intensidad;

    /** Esfuerzo percibido de 1 a 10; opcional e informativo. */
    @Column(name = "rpe")
    private Short rpe;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 11)
    private EstadoEntrenamiento estado;

    @Column(name = "kcal_estimadas", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcalEstimadas;

    @Column(name = "distancia_km", precision = 6, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "notas", length = 500)
    private String notas;

    public Entrenamiento() {
    }

    /** Si cuenta para el objetivo diario: los planificados y los realizados, no los omitidos. */
    public boolean cuentaParaObjetivo() {
        return estado == EstadoEntrenamiento.PLANIFICADO || estado == EstadoEntrenamiento.REALIZADO;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public TipoActividad getTipoActividad() {
        return tipoActividad;
    }

    public void setTipoActividad(TipoActividad tipoActividad) {
        this.tipoActividad = tipoActividad;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(OffsetDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public short getDuracionMin() {
        return duracionMin;
    }

    public void setDuracionMin(short duracionMin) {
        this.duracionMin = duracionMin;
    }

    public Intensidad getIntensidad() {
        return intensidad;
    }

    public void setIntensidad(Intensidad intensidad) {
        this.intensidad = intensidad;
    }

    public Short getRpe() {
        return rpe;
    }

    public void setRpe(Short rpe) {
        this.rpe = rpe;
    }

    public EstadoEntrenamiento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEntrenamiento estado) {
        this.estado = estado;
    }

    public BigDecimal getKcalEstimadas() {
        return kcalEstimadas;
    }

    public void setKcalEstimadas(BigDecimal kcalEstimadas) {
        this.kcalEstimadas = kcalEstimadas;
    }

    public BigDecimal getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(BigDecimal distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }
}
