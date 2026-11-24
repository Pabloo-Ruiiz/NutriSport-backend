package com.nutrisport.ia.entidad;

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
import java.math.BigDecimal;

/**
 * Una llamada al proveedor de IA (tabla {@code uso_ia}, §5.7): para qué función, cuántos tokens, cuánto
 * tardó, cuánto costó aproximadamente y si salió bien. Nunca guarda lo que se envió ni la respuesta.
 */
@Entity
@Table(name = "uso_ia")
public class UsoIa extends EntidadBase {

    /** Nulo en las llamadas del sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "funcion", nullable = false, length = 20)
    private FuncionIa funcion;

    @Column(name = "proveedor", nullable = false, length = 40)
    private String proveedor;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "tokens_entrada", nullable = false)
    private int tokensEntrada;

    @Column(name = "tokens_salida", nullable = false)
    private int tokensSalida;

    @Column(name = "latencia_ms", nullable = false)
    private int latenciaMs;

    /** En USD. */
    @Column(name = "coste_estimado", nullable = false, precision = 10, scale = 6)
    private BigDecimal costeEstimado = BigDecimal.ZERO;

    @Column(name = "exito", nullable = false)
    private boolean exito;

    public UsoIa() {
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public FuncionIa getFuncion() {
        return funcion;
    }

    public void setFuncion(FuncionIa funcion) {
        this.funcion = funcion;
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

    public int getTokensEntrada() {
        return tokensEntrada;
    }

    public void setTokensEntrada(int tokensEntrada) {
        this.tokensEntrada = tokensEntrada;
    }

    public int getTokensSalida() {
        return tokensSalida;
    }

    public void setTokensSalida(int tokensSalida) {
        this.tokensSalida = tokensSalida;
    }

    public int getLatenciaMs() {
        return latenciaMs;
    }

    public void setLatenciaMs(int latenciaMs) {
        this.latenciaMs = latenciaMs;
    }

    public BigDecimal getCosteEstimado() {
        return costeEstimado;
    }

    public void setCosteEstimado(BigDecimal costeEstimado) {
        this.costeEstimado = costeEstimado;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }
}
