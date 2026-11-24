package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/**
 * Petición bien formada que incumple una regla de negocio: 422 {@code REGLA_NEGOCIO} por defecto,
 * o un código más concreto como {@code PERFIL_REQUERIDO} o {@code NUTRICION_INCOMPLETA}.
 */
public class ReglaNegocioException extends ApiException {

    public ReglaNegocioException(String mensaje) {
        this("REGLA_NEGOCIO", mensaje);
    }

    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensaje);
    }
}
