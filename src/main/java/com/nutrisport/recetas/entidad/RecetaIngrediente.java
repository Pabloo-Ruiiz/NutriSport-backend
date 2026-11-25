package com.nutrisport.recetas.entidad;

import com.nutrisport.catalogo.entidad.Alimento;
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

/**
 * Ingrediente de una receta (tabla {@code receta_ingrediente}, §6.1). Conserva el texto original de
 * TheMealDB ("Chicken Breast", "200g") y, cuando se puede, el alimento del catálogo emparejado y la cantidad
 * en gramos. Se crea a través de {@link Receta#anadirIngrediente}, que fija su orden.
 */
@Entity
@Table(name = "receta_ingrediente")
public class RecetaIngrediente extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    /** Nulo mientras el ingrediente no está emparejado con un alimento del catálogo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alimento_id")
    private Alimento alimento;

    @Column(name = "nombre_original", nullable = false, length = 100)
    private String nombreOriginal;

    @Column(name = "medida_original", length = 60)
    private String medidaOriginal;

    /** Nulo si no se ha podido convertir la medida. */
    @Column(name = "cantidad_gramos", precision = 7, scale = 1)
    private BigDecimal cantidadGramos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conversion", nullable = false, length = 12)
    private EstadoConversion estadoConversion = EstadoConversion.SIN_DATOS;

    /** Posición en la receta, desde 1 (única por receta). */
    @Column(name = "orden", nullable = false)
    private short orden;

    public RecetaIngrediente() {
    }

    public RecetaIngrediente(String nombreOriginal, String medidaOriginal) {
        this.nombreOriginal = nombreOriginal;
        this.medidaOriginal = medidaOriginal;
    }

    public Receta getReceta() {
        return receta;
    }

    void setReceta(Receta receta) {
        this.receta = receta;
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public void setNombreOriginal(String nombreOriginal) {
        this.nombreOriginal = nombreOriginal;
    }

    public String getMedidaOriginal() {
        return medidaOriginal;
    }

    public void setMedidaOriginal(String medidaOriginal) {
        this.medidaOriginal = medidaOriginal;
    }

    public BigDecimal getCantidadGramos() {
        return cantidadGramos;
    }

    public void setCantidadGramos(BigDecimal cantidadGramos) {
        this.cantidadGramos = cantidadGramos;
    }

    public EstadoConversion getEstadoConversion() {
        return estadoConversion;
    }

    public void setEstadoConversion(EstadoConversion estadoConversion) {
        this.estadoConversion = estadoConversion;
    }

    public short getOrden() {
        return orden;
    }

    void setOrden(short orden) {
        this.orden = orden;
    }
}
