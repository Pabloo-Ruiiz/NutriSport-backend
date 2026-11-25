package com.nutrisport.menus.entidad;

import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.recetas.entidad.Receta;
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
 * Comida de un menú (tabla {@code comida_planificada}, §6.1): una receta con sus raciones en una fecha y una franja.
 * Se crea a través de {@link MenuSemanal#anadirComida}, que fija su orden. Su fecha debe estar dentro del rango del
 * menú ({@link MenuSemanal#contiene}).
 */
@Entity
@Table(name = "comida_planificada")
public class ComidaPlanificada extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_semanal_id", nullable = false)
    private MenuSemanal menu;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comida", nullable = false, length = 15)
    private TipoComida tipoComida;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    /** Mayor que 0 y como mucho 10 (CHECK ck_comida_planificada_raciones). */
    @Column(name = "raciones", nullable = false, precision = 4, scale = 2)
    private BigDecimal raciones;

    /** No cambia al regenerar el menú. */
    @Column(name = "bloqueada", nullable = false)
    private boolean bloqueada;

    @Column(name = "consumida", nullable = false)
    private boolean consumida;

    /** La comida registrada al marcarla como consumida; nula si no se registró o se borró el registro. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_comida_id")
    private RegistroComida registroComida;

    @Column(name = "orden", nullable = false)
    private short orden;

    public ComidaPlanificada() {
    }

    public ComidaPlanificada(LocalDate fecha, TipoComida tipoComida, Receta receta, BigDecimal raciones) {
        this.fecha = fecha;
        this.tipoComida = tipoComida;
        this.receta = receta;
        this.raciones = raciones;
    }

    public MenuSemanal getMenu() {
        return menu;
    }

    void setMenu(MenuSemanal menu) {
        this.menu = menu;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public TipoComida getTipoComida() {
        return tipoComida;
    }

    public void setTipoComida(TipoComida tipoComida) {
        this.tipoComida = tipoComida;
    }

    public Receta getReceta() {
        return receta;
    }

    public void setReceta(Receta receta) {
        this.receta = receta;
    }

    public BigDecimal getRaciones() {
        return raciones;
    }

    public void setRaciones(BigDecimal raciones) {
        this.raciones = raciones;
    }

    public boolean isBloqueada() {
        return bloqueada;
    }

    public void setBloqueada(boolean bloqueada) {
        this.bloqueada = bloqueada;
    }

    public boolean isConsumida() {
        return consumida;
    }

    public void setConsumida(boolean consumida) {
        this.consumida = consumida;
    }

    public RegistroComida getRegistroComida() {
        return registroComida;
    }

    /** Al enlazar un registro, la comida queda consumida (CHECK ck_comida_planificada_registro). */
    public void setRegistroComida(RegistroComida registroComida) {
        this.registroComida = registroComida;
        if (registroComida != null) {
            this.consumida = true;
        }
    }

    public short getOrden() {
        return orden;
    }

    void setOrden(short orden) {
        this.orden = orden;
    }
}
