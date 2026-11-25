package com.nutrisport.asistente.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Conversación de un usuario con el asistente de IA (tabla {@code conversacion_ia}, §3.H y §6.1, Ampliación). Sus
 * mensajes se consultan con {@code MensajeIaRepositorio}: una conversación larga no se carga entera.
 */
@Entity
@Table(name = "conversacion_ia")
public class ConversacionIa extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Nulo hasta el primer mensaje. */
    @Column(name = "titulo", length = 100)
    private String titulo;

    @Column(name = "archivada", nullable = false)
    private boolean archivada;

    /** Nulo mientras no tenga mensajes. */
    @Column(name = "ultimo_mensaje_en")
    private OffsetDateTime ultimoMensajeEn;

    public ConversacionIa() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public boolean isArchivada() {
        return archivada;
    }

    public void setArchivada(boolean archivada) {
        this.archivada = archivada;
    }

    public OffsetDateTime getUltimoMensajeEn() {
        return ultimoMensajeEn;
    }

    public void setUltimoMensajeEn(OffsetDateTime ultimoMensajeEn) {
        this.ultimoMensajeEn = ultimoMensajeEn;
    }
}
