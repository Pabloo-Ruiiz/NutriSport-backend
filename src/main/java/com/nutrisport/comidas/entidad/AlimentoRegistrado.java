package com.nutrisport.comidas.entidad;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Alimento de una comida (tabla {@code alimento_registrado}, §6.1). Guarda la cantidad tal como la
 * introdujo el usuario, su equivalencia en gramos y una <em>instantánea</em> de los nutrientes en el
 * momento del registro (§6.3): si después se corrige el alimento del catálogo, el historial no cambia.
 */
@Entity
@Table(name = "alimento_registrado")
public class AlimentoRegistrado extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_comida_id", nullable = false)
    private RegistroComida registroComida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alimento_id", nullable = false)
    private Alimento alimento;

    /** Cantidad introducida, en la {@link #unidad} indicada. */
    @Column(name = "cantidad", nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidad;

    /** "g", "ml" o el nombre de una porción casera del alimento. */
    @Column(name = "unidad", nullable = false, length = 40)
    private String unidad;

    /** Cantidad normalizada en gramos (o mililitros si la unidad base del alimento es ML). */
    @Column(name = "gramos", nullable = false, precision = 7, scale = 1)
    private BigDecimal gramos;

    @Column(name = "kcal", nullable = false, precision = 7, scale = 1)
    private BigDecimal kcal;

    @Column(name = "proteina", nullable = false, precision = 7, scale = 1)
    private BigDecimal proteina;

    @Column(name = "carbohidratos", nullable = false, precision = 7, scale = 1)
    private BigDecimal carbohidratos;

    @Column(name = "grasa", nullable = false, precision = 7, scale = 1)
    private BigDecimal grasa;

    /** Si el alimento lo propuso el análisis de una foto con IA. */
    @Column(name = "origen_ia", nullable = false)
    private boolean origenIa;

    /** Confianza de la IA entre 0 y 1; nula si no procede. */
    @Column(name = "confianza_ia", precision = 3, scale = 2)
    private BigDecimal confianzaIa;

    /** Posición dentro de la comida, desde 0. */
    @Column(name = "orden", nullable = false)
    private short orden;

    public AlimentoRegistrado() {
    }

    public RegistroComida getRegistroComida() {
        return registroComida;
    }

    /** Lo asigna {@link RegistroComida#anadirAlimento(AlimentoRegistrado)}. */
    void setRegistroComida(RegistroComida registroComida) {
        this.registroComida = registroComida;
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public BigDecimal getGramos() {
        return gramos;
    }

    public void setGramos(BigDecimal gramos) {
        this.gramos = gramos;
    }

    public BigDecimal getKcal() {
        return kcal;
    }

    public void setKcal(BigDecimal kcal) {
        this.kcal = kcal;
    }

    public BigDecimal getProteina() {
        return proteina;
    }

    public void setProteina(BigDecimal proteina) {
        this.proteina = proteina;
    }

    public BigDecimal getCarbohidratos() {
        return carbohidratos;
    }

    public void setCarbohidratos(BigDecimal carbohidratos) {
        this.carbohidratos = carbohidratos;
    }

    public BigDecimal getGrasa() {
        return grasa;
    }

    public void setGrasa(BigDecimal grasa) {
        this.grasa = grasa;
    }

    public boolean isOrigenIa() {
        return origenIa;
    }

    public void setOrigenIa(boolean origenIa) {
        this.origenIa = origenIa;
    }

    public BigDecimal getConfianzaIa() {
        return confianzaIa;
    }

    public void setConfianzaIa(BigDecimal confianzaIa) {
        this.confianzaIa = confianzaIa;
    }

    public short getOrden() {
        return orden;
    }

    /** Lo asigna {@link RegistroComida} según la posición en la lista. */
    void setOrden(short orden) {
        this.orden = orden;
    }
}
