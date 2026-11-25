package com.nutrisport.menus.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Sugerencia de la IA para una comida planificada (tabla {@code sugerencia_ia}, V15): un ajuste de cantidad o una
 * sustitución de un ingrediente, ya validada por el backend, con las cifras que calcula él. Las no válidas se guardan
 * con el motivo del descarte en el texto y no se pueden aplicar.
 */
@Entity
@Table(name = "sugerencia_ia")
public class SugerenciaIa extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comida_planificada_id", nullable = false)
    private ComidaPlanificada comida;

    /** La receta de la comida cuando se pidió (si la comida cambia de receta, la sugerencia queda obsoleta). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id", nullable = false)
    private Receta receta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 16)
    private TipoSugerencia tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receta_ingrediente_id")
    private RecetaIngrediente ingrediente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alimento_id")
    private Alimento alimento;

    @Column(name = "gramos", precision = 7, scale = 1)
    private BigDecimal gramos;

    @Column(name = "texto", nullable = false, length = 500)
    private String texto;

    @Column(name = "delta_kcal_racion", precision = 7, scale = 1)
    private BigDecimal deltaKcalRacion;

    @Column(name = "delta_proteina_racion", precision = 7, scale = 1)
    private BigDecimal deltaProteinaRacion;

    @Column(name = "valida", nullable = false)
    private boolean valida;

    @Column(name = "aplicada_en")
    private OffsetDateTime aplicadaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receta_derivada_id")
    private Receta recetaDerivada;

    public SugerenciaIa() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public ComidaPlanificada getComida() {
        return comida;
    }

    public void setComida(ComidaPlanificada comida) {
        this.comida = comida;
    }

    public Receta getReceta() {
        return receta;
    }

    public void setReceta(Receta receta) {
        this.receta = receta;
    }

    public TipoSugerencia getTipo() {
        return tipo;
    }

    public void setTipo(TipoSugerencia tipo) {
        this.tipo = tipo;
    }

    public RecetaIngrediente getIngrediente() {
        return ingrediente;
    }

    public void setIngrediente(RecetaIngrediente ingrediente) {
        this.ingrediente = ingrediente;
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public BigDecimal getGramos() {
        return gramos;
    }

    public void setGramos(BigDecimal gramos) {
        this.gramos = gramos;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public BigDecimal getDeltaKcalRacion() {
        return deltaKcalRacion;
    }

    public void setDeltaKcalRacion(BigDecimal deltaKcalRacion) {
        this.deltaKcalRacion = deltaKcalRacion;
    }

    public BigDecimal getDeltaProteinaRacion() {
        return deltaProteinaRacion;
    }

    public void setDeltaProteinaRacion(BigDecimal deltaProteinaRacion) {
        this.deltaProteinaRacion = deltaProteinaRacion;
    }

    public boolean isValida() {
        return valida;
    }

    public void setValida(boolean valida) {
        this.valida = valida;
    }

    public OffsetDateTime getAplicadaEn() {
        return aplicadaEn;
    }

    public void setAplicadaEn(OffsetDateTime aplicadaEn) {
        this.aplicadaEn = aplicadaEn;
    }

    public Receta getRecetaDerivada() {
        return recetaDerivada;
    }

    public void setRecetaDerivada(Receta recetaDerivada) {
        this.recetaDerivada = recetaDerivada;
    }
}
