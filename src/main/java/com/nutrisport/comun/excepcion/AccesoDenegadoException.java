package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/**
 * Operación no permitida al usuario: 403 {@code SIN_PERMISO} por defecto, o un código más concreto
 * como {@code EMAIL_NO_VERIFICADO} o {@code CONSENTIMIENTO_REVOCADO}.
 */
public class AccesoDenegadoException extends ApiException {

    public AccesoDenegadoException(String mensaje) {
        this("SIN_PERMISO", mensaje);
    }

    public AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
