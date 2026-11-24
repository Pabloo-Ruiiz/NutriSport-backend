package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/**
 * El recurso no existe o pertenece a otro usuario: 404 {@code NO_ENCONTRADO} por defecto, o un
 * código más concreto como {@code PERFIL_NO_ENCONTRADO}.
 */
public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(String mensaje) {
        this("NO_ENCONTRADO", mensaje);
    }

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
