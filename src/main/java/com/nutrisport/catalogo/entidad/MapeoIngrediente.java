package com.nutrisport.catalogo.entidad;

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
 * Emparejamiento reutilizable entre un nombre de ingrediente (de TheMealDB, en inglés) y un alimento del
 * catálogo (tabla {@code mapeo_ingrediente}, §6.1, Ampliación). Evita repetir búsquedas en USDA y permite que el
 * administrador corrija los emparejamientos.
 */
@Entity
@Table(name = "mapeo_ingrediente")
public class MapeoIngrediente extends EntidadBase {

    /** Nombre normalizado (minúsculas y sin tildes), único. */
    @Column(name = "nombre_normalizado", nullable = false, length = 100)
    private String nombreNormalizado;

    /** Nulo mientras no hay coincidencia fiable (pendiente de revisión). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alimento_id")
    private Alimento alimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", nullable = false, length = 10)
    private MetodoMapeo metodo;

    /** Similitud de texto (0–1) de la coincidencia; nula si no la hay. */
    @Column(name = "confianza", precision = 3, scale = 2)
    private BigDecimal confianza;

    @Column(name = "revisado", nullable = false)
    private boolean revisado;

    public MapeoIngrediente() {
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }

    public void setNombreNormalizado(String nombreNormalizado) {
        this.nombreNormalizado = nombreNormalizado;
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public MetodoMapeo getMetodo() {
        return metodo;
    }

    public void setMetodo(MetodoMapeo metodo) {
        this.metodo = metodo;
    }

    public BigDecimal getConfianza() {
        return confianza;
    }

    public void setConfianza(BigDecimal confianza) {
        this.confianza = confianza;
    }

    public boolean isRevisado() {
        return revisado;
    }

    public void setRevisado(boolean revisado) {
        this.revisado = revisado;
    }
}
