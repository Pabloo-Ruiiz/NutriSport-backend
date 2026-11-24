package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/** Un servicio del que depende la operación (proveedor de IA, almacenamiento de fotos) no responde: 503 {@code SERVICIO_NO_DISPONIBLE}. */
public class ServicioNoDisponibleException extends ApiException {

    public ServicioNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", mensaje);
    }
}
