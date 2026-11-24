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

/**
 * Categoría de alimentos o de recetas (tabla {@code categoria}, §6.1), con jerarquía opcional. Las
 * iniciales se cargan con R__categorias.sql.
 */
@Entity
@Table(name = "categoria")
public class Categoria extends EntidadBase {

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoCategoria tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private Categoria padre;

    /** Sección del supermercado para la lista de la compra; solo en las de tipo ALIMENTO. */
    @Column(name = "seccion_compra", length = 40)
    private String seccionCompra;

    @Column(name = "orden", nullable = false)
    private short orden;

    public Categoria() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoCategoria getTipo() {
        return tipo;
    }

    public void setTipo(TipoCategoria tipo) {
        this.tipo = tipo;
    }

    public Categoria getPadre() {
        return padre;
    }

    public void setPadre(Categoria padre) {
        this.padre = padre;
    }

    public String getSeccionCompra() {
        return seccionCompra;
    }

    public void setSeccionCompra(String seccionCompra) {
        this.seccionCompra = seccionCompra;
    }

    public short getOrden() {
        return orden;
    }

    public void setOrden(short orden) {
        this.orden = orden;
    }
}
