package com.nutrisport.compra.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.menus.entidad.MenuSemanal;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lista de la compra de un usuario (tabla {@code lista_compra}, §6.1, Ampliación), generada desde un menú o creada a
 * mano, con sus productos ordenados. Los productos se añaden y se quitan a través de la lista
 * ({@link #anadirProducto}, {@link #quitarProducto}): los quitados se borran al guardar.
 */
@Entity
@Table(name = "lista_compra")
public class ListaCompra extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Menú del que se generó; nulo si se creó a mano o si se borró el menú. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_semanal_id")
    private MenuSemanal menu;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Primer día del menú que cubre; nulo junto con {@link #fechaHasta} si no sale de un menú. */
    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoLista estado = EstadoLista.ACTIVA;

    @OneToMany(mappedBy = "lista", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<ProductoCompra> productos = new ArrayList<>();

    public ListaCompra() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    /** Añade un producto al final y fija su orden (único dentro de la lista). */
    public void anadirProducto(ProductoCompra producto) {
        short siguiente = (short) (productos.stream().mapToInt(ProductoCompra::getOrden).max().orElse(0) + 1);
        producto.setLista(this);
        producto.setOrden(siguiente);
        productos.add(producto);
    }

    /** Quita un producto (se borra al guardar: orphanRemoval). */
    public void quitarProducto(ProductoCompra producto) {
        if (productos.remove(producto)) {
            producto.setLista(null);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Getters y setters
    // ---------------------------------------------------------------------------------------------

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public MenuSemanal getMenu() {
        return menu;
    }

    public void setMenu(MenuSemanal menu) {
        this.menu = menu;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(LocalDate fechaDesde) {
        this.fechaDesde = fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(LocalDate fechaHasta) {
        this.fechaHasta = fechaHasta;
    }

    public EstadoLista getEstado() {
        return estado;
    }

    public void setEstado(EstadoLista estado) {
        this.estado = estado;
    }

    /** Productos en orden; para cambiarlos, {@link #anadirProducto} y {@link #quitarProducto}. */
    public List<ProductoCompra> getProductos() {
        return Collections.unmodifiableList(productos);
    }
}
