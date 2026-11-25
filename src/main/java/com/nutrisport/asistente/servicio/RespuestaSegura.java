package com.nutrisport.asistente.servicio;

import java.util.Objects;

/**
 * Respuesta fija a un mensaje sobre un tema sensible, que se da sin llamar al modelo (§4.4).
 *
 * @param motivo qué tipo de tema se ha detectado
 * @param texto  la respuesta que se guarda como mensaje del asistente
 */
public record RespuestaSegura(Motivo motivo, String texto) {

    /** Temas que el asistente no trata. */
    public enum Motivo {
        /** Restricción extrema, pérdidas de peso rápidas, vómitos, laxantes, atracones… */
        CONDUCTA_ALIMENTARIA_RIESGO,
        /** Dosis de medicamentos o de suplementos. */
        DOSIS_MEDICAMENTOS,
        /** Síntomas, diagnósticos, embarazo, lesiones o patologías. */
        CONSULTA_MEDICA
    }

    public RespuestaSegura {
        Objects.requireNonNull(motivo, "motivo");
        Objects.requireNonNull(texto, "texto");
    }
}
