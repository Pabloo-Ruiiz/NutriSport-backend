package com.nutrisport.compra.entidad;

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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Producto de una lista de la compra (tabla {@code producto_compra}, §6.1): un alimento del catálogo con la cantidad
 * sumada del menú, o un producto libre (solo {@code nombreLibre}). Se crea a través de {@link ListaCompra#anadirProducto},
 * que fija su orden.
 */
@Entity
@Table(name = "producto_compra")
public class ProductoCompra extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_compra_id", nullable = false)
    private ListaCompra lista;

    /** Nulo en los productos libres o si se borró el alimento. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alimento_id")
    private Alimento alimento;

    /**
     * Texto del producto libre; en los de un alimento, copia de su nombre (así el producto sigue teniendo nombre si se
     * borra el alimento: CHECK ck_producto_compra_nombre).
     */
    @Column(name = "nombre_libre", length = 120)
    private String nombreLibre;

    /** Mayor que 0, con dos decimales. */
    @Column(name = "cantidad", nullable = false, precision = 9, scale = 2)
    private BigDecimal cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad", nullable = false, length = 5)
    private UnidadCompra unidad;

    /** Sección del supermercado: Categoria.seccionCompra del alimento u "Otros". */
    @Column(name = "seccion", nullable = false, length = 40)
    private String seccion;

    @Column(name = "comprado", nullable = false)
    private boolean comprado;

    /** Añadido a mano por el usuario (no sale del menú). */
    @Column(name = "manual", nullable = false)
    private boolean manual;

    /** Cantidad por día del menú, en orden de fecha (columna jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desglose", nullable = false)
    private List<EntradaDesglose> desglose = new ArrayList<>();

    @Column(name = "orden", nullable = false)
    private short orden;

    public ProductoCompra() {
    }

    /** Nombre para mostrar: el del alimento o, si no hay, el texto libre. */
    public String getNombre() {
        return alimento != null ? alimento.getNombre() : nombreLibre;
    }

    public ListaCompra getLista() {
        return lista;
    }

    void setLista(ListaCompra lista) {
        this.lista = lista;
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public String getNombreLibre() {
        return nombreLibre;
    }

    public void setNombreLibre(String nombreLibre) {
        this.nombreLibre = nombreLibre;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public UnidadCompra getUnidad() {
        return unidad;
    }

    public void setUnidad(UnidadCompra unidad) {
        this.unidad = unidad;
    }

    public String getSeccion() {
        return seccion;
    }

    public void setSeccion(String seccion) {
        this.seccion = seccion;
    }

    public boolean isComprado() {
        return comprado;
    }

    public void setComprado(boolean comprado) {
        this.comprado = comprado;
    }

    public boolean isManual() {
        return manual;
    }

    public void setManual(boolean manual) {
        this.manual = manual;
    }

    public List<EntradaDesglose> getDesglose() {
        return desglose;
    }

    public void setDesglose(List<EntradaDesglose> desglose) {
        this.desglose = desglose == null ? new ArrayList<>() : new ArrayList<>(desglose);
    }

    public short getOrden() {
        return orden;
    }

    void setOrden(short orden) {
        this.orden = orden;
    }
}
