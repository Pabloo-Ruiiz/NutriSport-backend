package com.nutrisport.ia.puerto;

/**
 * Conversación con el asistente nutricional (§3.H y §4.4). El adaptador real usa Spring AI con las herramientas de
 * solo lectura de {@code HerramientasAsistente}; el simulado responde sin proveedor para desarrollo y tests.
 */
public interface ChatAsistentePuerto {

    /**
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException si el proveedor no responde
     */
    RespuestaChat responder(ContextoChat contexto);
}
