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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Objetivo nutricional de un día concreto (tabla {@code objetivo_diario}, §6.1), uno por usuario y
 * fecha. Se guarda por día para que el historial y la adherencia no cambien cuando el usuario actualiza
 * su perfil (§6.3). El gasto de los entrenamientos se suma a la energía y se asigna entero a los
 * carbohidratos: {@code kcalTotal = kcalBase + kcalEntreno} y
 * {@code carbohidratosTotalG = carbohidratosG + kcalEntreno / 4}.
 */
@Entity
@Table(name = "objetivo_diario")
public class ObjetivoDiario extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /** Objetivos base del perfil ese día (sin entrenamiento). */
    @Column(name = "kcal_base", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcalBase;

    @Column(name = "proteina_g", nullable = false, precision = 7, scale = 1)
    private BigDecimal proteinaG;

    @Column(name = "carbohidratos_g", nullable = false, precision = 7, scale = 1)
    private BigDecimal carbohidratosG;

    @Column(name = "grasa_g", nullable = false, precision = 7, scale = 1)
    private BigDecimal grasaG;

    /** Suma del gasto neto de los entrenamientos del día que cuentan (planificados y realizados). */
    @Column(name = "kcal_entreno", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcalEntreno;

    @Column(name = "kcal_total", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcalTotal;

    @Column(name = "carbohidratos_total_g", nullable = false, precision = 7, scale = 1)
    private BigDecimal carbohidratosTotalG;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    private OrigenObjetivo origen;

    public ObjetivoDiario() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getKcalBase() {
        return kcalBase;
    }

    public void setKcalBase(BigDecimal kcalBase) {
        this.kcalBase = kcalBase;
    }

    public BigDecimal getProteinaG() {
        return proteinaG;
    }

    public void setProteinaG(BigDecimal proteinaG) {
        this.proteinaG = proteinaG;
    }

    public BigDecimal getCarbohidratosG() {
        return carbohidratosG;
    }

    public void setCarbohidratosG(BigDecimal carbohidratosG) {
        this.carbohidratosG = carbohidratosG;
    }

    public BigDecimal getGrasaG() {
        return grasaG;
    }

    public void setGrasaG(BigDecimal grasaG) {
        this.grasaG = grasaG;
    }

    public BigDecimal getKcalEntreno() {
        return kcalEntreno;
    }

    public void setKcalEntreno(BigDecimal kcalEntreno) {
        this.kcalEntreno = kcalEntreno;
    }

    public BigDecimal getKcalTotal() {
        return kcalTotal;
    }

    public void setKcalTotal(BigDecimal kcalTotal) {
        this.kcalTotal = kcalTotal;
    }

    public BigDecimal getCarbohidratosTotalG() {
        return carbohidratosTotalG;
    }

    public void setCarbohidratosTotalG(BigDecimal carbohidratosTotalG) {
        this.carbohidratosTotalG = carbohidratosTotalG;
    }

    public OrigenObjetivo getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenObjetivo origen) {
        this.origen = origen;
    }
}
