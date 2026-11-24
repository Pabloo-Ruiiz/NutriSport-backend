package com.nutrisport.actividad.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.Hibernate;

/**
 * Tipo de actividad del catálogo (tabla {@code tipo_actividad}, §6.1) con su equivalente metabólico
 * (MET). Su clave es el código, por eso no extiende EntidadBase ni tiene columnas de auditoría. Se
 * carga con R__tipos_actividad.sql.
 */
@Entity
@Table(name = "tipo_actividad")
public class TipoActividad {

    /** Código estable, p. ej. {@code CARRERA_9_7KMH}; lo referencian perfiles y entrenamientos. */
    @Id
    @Column(name = "codigo", nullable = false, updatable = false, length = 40)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    /** Equivalente metabólico del Compendium of Physical Activities (2011). */
    @Column(name = "met", nullable = false, precision = 4, scale = 1)
    private BigDecimal met;

    @Column(name = "categoria", nullable = false, length = 30)
    private String categoria;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    public TipoActividad() {
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getMet() {
        return met;
    }

    public void setMet(BigDecimal met) {
        this.met = met;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /** Dos tipos son iguales si tienen el mismo código (también entre un proxy perezoso y la entidad). */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (otro == null || Hibernate.getClass(this) != Hibernate.getClass(otro)) {
            return false;
        }
        TipoActividad tipo = (TipoActividad) otro;
        return codigo != null && codigo.equals(tipo.getCodigo());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
