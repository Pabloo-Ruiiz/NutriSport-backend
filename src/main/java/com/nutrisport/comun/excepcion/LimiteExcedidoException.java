package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/**
 * Demasiados intentos de login o límite diario de IA superado: 429 {@code LIMITE_EXCEDIDO}.
 * La respuesta incluye la cabecera {@code Retry-After} con {@link #getSegundosReintento()}.
 */
public class LimiteExcedidoException extends ApiException {

    private final long segundosReintento;

    public LimiteExcedidoException(String mensaje, long segundosReintento) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_EXCEDIDO", mensaje);
        this.segundosReintento = segundosReintento;
    }

    public long getSegundosReintento() {
        return segundosReintento;
    }
}
