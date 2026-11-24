package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/** Estado incompatible con la operación: 409 {@code CONFLICTO} por defecto, o un código más concreto. */
public class ConflictoException extends ApiException {

    public ConflictoException(String mensaje) {
        this("CONFLICTO", mensaje);
    }

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
