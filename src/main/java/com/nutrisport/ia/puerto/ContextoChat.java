package com.nutrisport.ia.puerto;

import com.nutrisport.asistente.entidad.MensajeIa;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que necesita el asistente para responder un mensaje (§4.4). Nunca lleva el nombre ni el email del usuario.
 *
 * @param usuarioId      solo para el contexto de las herramientas (no se envía al modelo)
 * @param perfilResumido edad aproximada, objetivo, deporte y nivel de actividad
 * @param restricciones  restricciones estrictas en palabras, o "ninguna"
 * @param historial      mensajes anteriores de la conversación en orden cronológico (sin el actual)
 * @param mensaje        el mensaje del usuario que hay que responder
 */
public record ContextoChat(UUID usuarioId, String perfilResumido, String restricciones, List<MensajeIa> historial,
        String mensaje) {

    public ContextoChat {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(mensaje, "mensaje");
        historial = historial == null ? List.of() : List.copyOf(historial);
    }
}
