package com.nutrisport.seguimiento.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Patrón detectado en los datos de un usuario (tabla {@code insight}, §4.6 y §6.1, Ampliación). Las cifras están en
 * {@code datos}, calculadas por las reglas; el título y el texto se redactan a partir de ellas. Solo los de la última
 * detección están vigentes.
 */
@Entity
@Table(name = "insight")
public class Insight extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoInsight tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", nullable = false, length = 12)
    private SeveridadInsight severidad;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "texto", nullable = false, length = 600)
    private String texto;

    /** Cifras calculadas por las reglas (columna jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos", nullable = false)
    private Map<String, Object> datos = new LinkedHashMap<>();

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(name = "vigente", nullable = false)
    private boolean vigente = true;

    public Insight() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public TipoInsight getTipo() {
        return tipo;
    }

    public void setTipo(TipoInsight tipo) {
        this.tipo = tipo;
    }

    public SeveridadInsight getSeveridad() {
        return severidad;
    }

    public void setSeveridad(SeveridadInsight severidad) {
        this.severidad = severidad;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Map<String, Object> getDatos() {
        return datos;
    }

    public void setDatos(Map<String, Object> datos) {
        this.datos = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
    }

    public LocalDate getPeriodoDesde() {
        return periodoDesde;
    }

    public void setPeriodoDesde(LocalDate periodoDesde) {
        this.periodoDesde = periodoDesde;
    }

    public LocalDate getPeriodoHasta() {
        return periodoHasta;
    }

    public void setPeriodoHasta(LocalDate periodoHasta) {
        this.periodoHasta = periodoHasta;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }
}
