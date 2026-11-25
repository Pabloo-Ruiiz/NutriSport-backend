package com.nutrisport.asistente.entidad;

import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Mensaje de una conversación con el asistente (tabla {@code mensaje_ia}, §6.1). En las respuestas del asistente se
 * guardan las herramientas de solo lectura que usó y los tokens consumidos.
 */
@Entity
@Table(name = "mensaje_ia")
public class MensajeIa extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private ConversacionIa conversacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 12)
    private RolMensaje rol;

    @Column(name = "contenido", nullable = false, columnDefinition = "text")
    private String contenido;

    /**
     * Nombres de las herramientas usadas para responder (columna jsonb: ["resumenDiario", ...]), con un conversor
     * propio ({@link ListaTextosJsonConverter} explica por qué).
     */
    @Convert(converter = ListaTextosJsonConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "herramientas_usadas", nullable = false, columnDefinition = "jsonb")
    private List<String> herramientasUsadas = List.of();

    @Column(name = "tokens_entrada")
    private Integer tokensEntrada;

    @Column(name = "tokens_salida")
    private Integer tokensSalida;

    /** −1 (no útil), 1 (útil) o nulo (sin valorar). */
    @Column(name = "valoracion")
    private Short valoracion;

    public MensajeIa() {
    }

    public MensajeIa(ConversacionIa conversacion, RolMensaje rol, String contenido) {
        this.conversacion = conversacion;
        this.rol = rol;
        this.contenido = contenido;
    }

    public ConversacionIa getConversacion() {
        return conversacion;
    }

    public void setConversacion(ConversacionIa conversacion) {
        this.conversacion = conversacion;
    }

    public RolMensaje getRol() {
        return rol;
    }

    public void setRol(RolMensaje rol) {
        this.rol = rol;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    /** En el orden en que se usaron; no se puede modificar. */
    public List<String> getHerramientasUsadas() {
        return herramientasUsadas;
    }

    public void setHerramientasUsadas(List<String> herramientasUsadas) {
        this.herramientasUsadas = herramientasUsadas == null ? List.of() : List.copyOf(herramientasUsadas);
    }

    public Integer getTokensEntrada() {
        return tokensEntrada;
    }

    public void setTokensEntrada(Integer tokensEntrada) {
        this.tokensEntrada = tokensEntrada;
    }

    public Integer getTokensSalida() {
        return tokensSalida;
    }

    public void setTokensSalida(Integer tokensSalida) {
        this.tokensSalida = tokensSalida;
    }

    public Short getValoracion() {
        return valoracion;
    }

    public void setValoracion(Short valoracion) {
        this.valoracion = valoracion;
    }
}
