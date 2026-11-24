package com.nutrisport.perfil.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Perfil deportivo y nutricional del usuario (tabla {@code perfil_deportivo}, §3.B y §6.1). Los
 * objetivos guardados son los <em>base</em> (sin entrenamiento); si {@code objetivosManuales} es
 * {@code true}, el usuario los fijó a mano y no se recalculan.
 */
@Entity
@Table(name = "perfil_deportivo")
public class PerfilDeportivo extends EntidadBase {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    /** El usuario debe ser mayor de edad (lo valida el servicio). */
    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", nullable = false, length = 10)
    private Sexo sexo;

    @Column(name = "altura_cm", nullable = false, precision = 5, scale = 1)
    private BigDecimal alturaCm;

    /** Sincronizado con el último registro de peso. */
    @Column(name = "peso_actual_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal pesoActualKg;

    @Column(name = "porcentaje_grasa", precision = 4, scale = 1)
    private BigDecimal porcentajeGrasa;

    @Enumerated(EnumType.STRING)
    @Column(name = "objetivo", nullable = false, length = 20)
    private ObjetivoDeportivo objetivo;

    /** Obligatoria solo si el objetivo es {@link ObjetivoDeportivo#COMPETICION}. */
    @Column(name = "fecha_competicion")
    private LocalDate fechaCompeticion;

    /** Código del tipo de actividad; pasará a ser relación cuando exista la tabla tipo_actividad. */
    @Column(name = "deporte_principal", nullable = false, length = 40)
    private String deportePrincipal;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_actividad", nullable = false, length = 12)
    private NivelActividad nivelActividad;

    @Column(name = "dias_entrenamiento_semana", nullable = false)
    private Short diasEntrenamientoSemana;

    @Column(name = "duracion_media_min", nullable = false)
    private Short duracionMediaMin;

    @Column(name = "comidas_por_dia", nullable = false)
    private Short comidasPorDia;

    @Column(name = "tiempo_max_cocina_min")
    private Short tiempoMaxCocinaMin;

    @Column(name = "objetivo_kcal", precision = 7, scale = 1)
    private BigDecimal objetivoKcal;

    @Column(name = "objetivo_proteina_g", precision = 7, scale = 1)
    private BigDecimal objetivoProteinaG;

    @Column(name = "objetivo_carbohidratos_g", precision = 7, scale = 1)
    private BigDecimal objetivoCarbohidratosG;

    @Column(name = "objetivo_grasa_g", precision = 7, scale = 1)
    private BigDecimal objetivoGrasaG;

    @Column(name = "objetivos_manuales", nullable = false)
    private boolean objetivosManuales;

    public PerfilDeportivo() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public Sexo getSexo() {
        return sexo;
    }

    public void setSexo(Sexo sexo) {
        this.sexo = sexo;
    }

    public BigDecimal getAlturaCm() {
        return alturaCm;
    }

    public void setAlturaCm(BigDecimal alturaCm) {
        this.alturaCm = alturaCm;
    }

    public BigDecimal getPesoActualKg() {
        return pesoActualKg;
    }

    public void setPesoActualKg(BigDecimal pesoActualKg) {
        this.pesoActualKg = pesoActualKg;
    }

    public BigDecimal getPorcentajeGrasa() {
        return porcentajeGrasa;
    }

    public void setPorcentajeGrasa(BigDecimal porcentajeGrasa) {
        this.porcentajeGrasa = porcentajeGrasa;
    }

    public ObjetivoDeportivo getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(ObjetivoDeportivo objetivo) {
        this.objetivo = objetivo;
    }

    public LocalDate getFechaCompeticion() {
        return fechaCompeticion;
    }

    public void setFechaCompeticion(LocalDate fechaCompeticion) {
        this.fechaCompeticion = fechaCompeticion;
    }

    public String getDeportePrincipal() {
        return deportePrincipal;
    }

    public void setDeportePrincipal(String deportePrincipal) {
        this.deportePrincipal = deportePrincipal;
    }

    public NivelActividad getNivelActividad() {
        return nivelActividad;
    }

    public void setNivelActividad(NivelActividad nivelActividad) {
        this.nivelActividad = nivelActividad;
    }

    public Short getDiasEntrenamientoSemana() {
        return diasEntrenamientoSemana;
    }

    public void setDiasEntrenamientoSemana(Short diasEntrenamientoSemana) {
        this.diasEntrenamientoSemana = diasEntrenamientoSemana;
    }

    public Short getDuracionMediaMin() {
        return duracionMediaMin;
    }

    public void setDuracionMediaMin(Short duracionMediaMin) {
        this.duracionMediaMin = duracionMediaMin;
    }

    public Short getComidasPorDia() {
        return comidasPorDia;
    }

    public void setComidasPorDia(Short comidasPorDia) {
        this.comidasPorDia = comidasPorDia;
    }

    public Short getTiempoMaxCocinaMin() {
        return tiempoMaxCocinaMin;
    }

    public void setTiempoMaxCocinaMin(Short tiempoMaxCocinaMin) {
        this.tiempoMaxCocinaMin = tiempoMaxCocinaMin;
    }

    public BigDecimal getObjetivoKcal() {
        return objetivoKcal;
    }

    public void setObjetivoKcal(BigDecimal objetivoKcal) {
        this.objetivoKcal = objetivoKcal;
    }

    public BigDecimal getObjetivoProteinaG() {
        return objetivoProteinaG;
    }

    public void setObjetivoProteinaG(BigDecimal objetivoProteinaG) {
        this.objetivoProteinaG = objetivoProteinaG;
    }

    public BigDecimal getObjetivoCarbohidratosG() {
        return objetivoCarbohidratosG;
    }

    public void setObjetivoCarbohidratosG(BigDecimal objetivoCarbohidratosG) {
        this.objetivoCarbohidratosG = objetivoCarbohidratosG;
    }

    public BigDecimal getObjetivoGrasaG() {
        return objetivoGrasaG;
    }

    public void setObjetivoGrasaG(BigDecimal objetivoGrasaG) {
        this.objetivoGrasaG = objetivoGrasaG;
    }

    public boolean isObjetivosManuales() {
        return objetivosManuales;
    }

    public void setObjetivosManuales(boolean objetivosManuales) {
        this.objetivosManuales = objetivosManuales;
    }
}
