package com.nutrisport.menus.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
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
 * Menú diario o semanal de un usuario (tabla {@code menu_semanal}, §6.1, Ampliación), con sus comidas planificadas
 * ordenadas por fecha y orden. Las comidas se añaden y se quitan a través del menú ({@link #anadirComida},
 * {@link #quitarComida}): las quitadas se borran al guardar.
 */
@Entity
@Table(name = "menu_semanal")
public class MenuSemanal extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 8)
    private TipoMenu tipo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "favorito", nullable = false)
    private boolean favorito;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoMenu estado = EstadoMenu.BORRADOR;

    /** Generado por el algoritmo de reglas (§4.5). */
    @Column(name = "generado_automaticamente", nullable = false)
    private boolean generadoAutomaticamente;

    /** Explicación opcional del menú (LLM). */
    @Column(name = "explicacion", columnDefinition = "text")
    private String explicacion;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC, orden ASC")
    private List<ComidaPlanificada> comidas = new ArrayList<>();

    public MenuSemanal() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    /** Si la fecha está dentro del rango del menú (ambos extremos incluidos). */
    public boolean contiene(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }

    /** Añade una comida al final y fija su orden (único dentro del menú). */
    public void anadirComida(ComidaPlanificada comida) {
        short siguiente = (short) (comidas.stream().mapToInt(ComidaPlanificada::getOrden).max().orElse(0) + 1);
        comida.setMenu(this);
        comida.setOrden(siguiente);
        comidas.add(comida);
    }

    /** Quita una comida (se borra al guardar: orphanRemoval). */
    public void quitarComida(ComidaPlanificada comida) {
        if (comidas.remove(comida)) {
            comida.setMenu(null);
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public TipoMenu getTipo() {
        return tipo;
    }

    public void setTipo(TipoMenu tipo) {
        this.tipo = tipo;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public boolean isFavorito() {
        return favorito;
    }

    public void setFavorito(boolean favorito) {
        this.favorito = favorito;
    }

    public EstadoMenu getEstado() {
        return estado;
    }

    public void setEstado(EstadoMenu estado) {
        this.estado = estado;
    }

    public boolean isGeneradoAutomaticamente() {
        return generadoAutomaticamente;
    }

    public void setGeneradoAutomaticamente(boolean generadoAutomaticamente) {
        this.generadoAutomaticamente = generadoAutomaticamente;
    }

    public String getExplicacion() {
        return explicacion;
    }

    public void setExplicacion(String explicacion) {
        this.explicacion = explicacion;
    }

    /** Comidas por fecha y orden; para cambiarlas, {@link #anadirComida} y {@link #quitarComida}. */
    public List<ComidaPlanificada> getComidas() {
        return Collections.unmodifiableList(comidas);
    }
}
