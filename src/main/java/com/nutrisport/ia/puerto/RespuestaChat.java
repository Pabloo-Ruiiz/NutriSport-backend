package com.nutrisport.ia.puerto;

import java.util.List;

/**
 * Respuesta del asistente (§4.4).
 *
 * @param herramientasUsadas herramientas de solo lectura que invocó el modelo, en orden (puede repetir)
 * @param modelo             modelo que respondió; nulo si el proveedor no lo informa
 */
public record RespuestaChat(String texto, List<String> herramientasUsadas, int tokensEntrada, int tokensSalida,
        String modelo) {

    public RespuestaChat {
        herramientasUsadas = herramientasUsadas == null ? List.of() : List.copyOf(herramientasUsadas);
    }
}
