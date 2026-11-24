package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/** El recurso no existe o pertenece a otro usuario: 404 {@code NO_ENCONTRADO}. */
public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(String mensaje) {
        super(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }
}
