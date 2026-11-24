package com.nutrisport.comidas.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.ia.puerto.AlimentoDetectado;
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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Análisis de una foto con IA (tabla {@code analisis_imagen}, §4.3 y §6.1). Guarda el contexto enviado,
 * con qué proveedor, modelo y versión de prompt se hizo (reproducibilidad), lo que devolvió la IA, los
 * candidatos emparejados con el catálogo y lo que corrigió el usuario.
 */
@Entity
@Table(name = "analisis_imagen")
public class AnalisisImagen extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoAnalisis tipo;

    /** Nula cuando la foto se ha borrado por retención. */
    @Column(name = "imagen_key", length = 300)
    private String imagenKey;

    @Column(name = "miniatura_key", length = 300)
    private String miniaturaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoAnalisis estado = EstadoAnalisis.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comida", length = 15)
    private TipoComida tipoComida;

    @Column(name = "descripcion_usuario", length = 500)
    private String descripcionUsuario;

    @Column(name = "proveedor", length = 40)
    private String proveedor;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "version_prompt", length = 20)
    private String versionPrompt;

    /** Lo que devolvió la IA, tal cual (jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado_bruto")
    private List<AlimentoDetectado> resultadoBruto = new ArrayList<>();

    /** Candidatos emparejados con el catálogo (jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidatos")
    private List<CandidatoAnalisis> candidatos = new ArrayList<>();

    /** Diferencias entre la propuesta y lo confirmado (jsonb), para medir la precisión real. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correcciones")
    private String correcciones;

    @Column(name = "latencia_ms")
    private Integer latenciaMs;

    /** En USD. */
    @Column(name = "coste_estimado", precision = 10, scale = 6)
    private BigDecimal costeEstimado;

    /** Comida creada al confirmar (tipo COMIDA). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_comida_id")
    private RegistroComida registroComida;

    /** Alimento creado al confirmar una etiqueta (tipo ETIQUETA, Visión). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alimento_creado_id")
    private Alimento alimentoCreado;

    @Column(name = "error_mensaje", length = 300)
    private String errorMensaje;

    @Column(name = "completado_en")
    private OffsetDateTime completadoEn;

    public AnalisisImagen() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public TipoAnalisis getTipo() {
        return tipo;
    }

    public void setTipo(TipoAnalisis tipo) {
        this.tipo = tipo;
    }

    public String getImagenKey() {
        return imagenKey;
    }

    public void setImagenKey(String imagenKey) {
        this.imagenKey = imagenKey;
    }

    public String getMiniaturaKey() {
        return miniaturaKey;
    }

    public void setMiniaturaKey(String miniaturaKey) {
        this.miniaturaKey = miniaturaKey;
    }

    public EstadoAnalisis getEstado() {
        return estado;
    }

    public void setEstado(EstadoAnalisis estado) {
        this.estado = estado;
    }

    public TipoComida getTipoComida() {
        return tipoComida;
    }

    public void setTipoComida(TipoComida tipoComida) {
        this.tipoComida = tipoComida;
    }

    public String getDescripcionUsuario() {
        return descripcionUsuario;
    }

    public void setDescripcionUsuario(String descripcionUsuario) {
        this.descripcionUsuario = descripcionUsuario;
    }

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getVersionPrompt() {
        return versionPrompt;
    }

    public void setVersionPrompt(String versionPrompt) {
        this.versionPrompt = versionPrompt;
    }

    public List<AlimentoDetectado> getResultadoBruto() {
        return resultadoBruto;
    }

    public void setResultadoBruto(List<AlimentoDetectado> resultadoBruto) {
        this.resultadoBruto = resultadoBruto == null ? new ArrayList<>() : new ArrayList<>(resultadoBruto);
    }

    public List<CandidatoAnalisis> getCandidatos() {
        return candidatos;
    }

    public void setCandidatos(List<CandidatoAnalisis> candidatos) {
        this.candidatos = candidatos == null ? new ArrayList<>() : new ArrayList<>(candidatos);
    }

    public String getCorrecciones() {
        return correcciones;
    }

    public void setCorrecciones(String correcciones) {
        this.correcciones = correcciones;
    }

    public Integer getLatenciaMs() {
        return latenciaMs;
    }

    public void setLatenciaMs(Integer latenciaMs) {
        this.latenciaMs = latenciaMs;
    }

    public BigDecimal getCosteEstimado() {
        return costeEstimado;
    }

    public void setCosteEstimado(BigDecimal costeEstimado) {
        this.costeEstimado = costeEstimado;
    }

    public RegistroComida getRegistroComida() {
        return registroComida;
    }

    public void setRegistroComida(RegistroComida registroComida) {
        this.registroComida = registroComida;
    }

    public Alimento getAlimentoCreado() {
        return alimentoCreado;
    }

    public void setAlimentoCreado(Alimento alimentoCreado) {
        this.alimentoCreado = alimentoCreado;
    }

    public String getErrorMensaje() {
        return errorMensaje;
    }

    public void setErrorMensaje(String errorMensaje) {
        this.errorMensaje = errorMensaje;
    }

    public OffsetDateTime getCompletadoEn() {
        return completadoEn;
    }

    public void setCompletadoEn(OffsetDateTime completadoEn) {
        this.completadoEn = completadoEn;
    }
}
