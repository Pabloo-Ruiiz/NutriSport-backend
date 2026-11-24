package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/** El proveedor de IA no responde: 503 {@code SERVICIO_NO_DISPONIBLE}. */
public class ServicioNoDisponibleException extends ApiException {

    public ServicioNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", mensaje);
    }
}
