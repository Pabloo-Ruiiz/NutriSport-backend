package com.nutrisport.ia.puerto;

/**
 * Respuesta de un puerto de IA con el uso que informa el proveedor (para UsoIaServicio).
 *
 * @param contenido     resultado útil (texto, objeto estructurado...)
 * @param tokensEntrada tokens de entrada; 0 si el proveedor no lo informa o en modo simulado
 * @param tokensSalida  tokens de salida; 0 si el proveedor no lo informa o en modo simulado
 * @param modelo        modelo que respondió; {@code "simulado"} en modo simulado
 */
public record ResultadoIa<T>(T contenido, int tokensEntrada, int tokensSalida, String modelo) {
}
