package com.nutrisport.comidas.entidad;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Comida registrada por un usuario (tabla {@code registro_comida}, §6.1) con sus alimentos. Los totales
 * están desnormalizados (§6.3): quien cambie los alimentos debe llamar a {@link #recalcularTotales()}
 * antes de que termine la transacción.
 */
@Entity
@Table(name = "registro_comida")
public class RegistroComida extends EntidadBase {

    private static final int DECIMALES_TOTALES = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora")
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comida", nullable = false, length = 15)
    private TipoComida tipoComida;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 14)
    private OrigenRegistro origen;

    @Column(name = "notas", length = 500)
    private String notas;

    @Column(name = "total_kcal", nullable = false, precision = 8, scale = 1)
    private BigDecimal totalKcal = BigDecimal.ZERO;

    @Column(name = "total_proteina", nullable = false, precision = 8, scale = 1)
    private BigDecimal totalProteina = BigDecimal.ZERO;

    @Column(name = "total_carbohidratos", nullable = false, precision = 8, scale = 1)
    private BigDecimal totalCarbohidratos = BigDecimal.ZERO;

    @Column(name = "total_grasa", nullable = false, precision = 8, scale = 1)
    private BigDecimal totalGrasa = BigDecimal.ZERO;

    /** Se gestionan solo desde aquí: quitar uno de la lista lo borra (orphanRemoval). */
    @OneToMany(mappedBy = "registroComida", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<AlimentoRegistrado> alimentos = new ArrayList<>();

    public RegistroComida() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    /** Añade el alimento al final de la comida y le asigna su orden. No recalcula los totales. */
    public void anadirAlimento(AlimentoRegistrado alimento) {
        alimento.setRegistroComida(this);
        alimento.setOrden((short) alimentos.size());
        alimentos.add(alimento);
    }

    /** Quita todos los alimentos (se borrarán al guardar). No recalcula los totales. */
    public void vaciarAlimentos() {
        alimentos.clear();
    }

    /** Suma la instantánea nutricional de los alimentos en los cuatro totales, con un decimal. */
    public void recalcularTotales() {
        totalKcal = sumar(AlimentoRegistrado::getKcal);
        totalProteina = sumar(AlimentoRegistrado::getProteina);
        totalCarbohidratos = sumar(AlimentoRegistrado::getCarbohidratos);
        totalGrasa = sumar(AlimentoRegistrado::getGrasa);
    }

    private BigDecimal sumar(Function<AlimentoRegistrado, BigDecimal> nutriente) {
        return alimentos.stream()
                .map(nutriente)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(DECIMALES_TOTALES, RoundingMode.HALF_UP);
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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public TipoComida getTipoComida() {
        return tipoComida;
    }

    public void setTipoComida(TipoComida tipoComida) {
        this.tipoComida = tipoComida;
    }

    public OrigenRegistro getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenRegistro origen) {
        this.origen = origen;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public BigDecimal getTotalKcal() {
        return totalKcal;
    }

    public BigDecimal getTotalProteina() {
        return totalProteina;
    }

    public BigDecimal getTotalCarbohidratos() {
        return totalCarbohidratos;
    }

    public BigDecimal getTotalGrasa() {
        return totalGrasa;
    }

    /** Vista de solo lectura: para cambiarlos, {@link #anadirAlimento} y {@link #vaciarAlimentos}. */
    public List<AlimentoRegistrado> getAlimentos() {
        return Collections.unmodifiableList(alimentos);
    }
}
