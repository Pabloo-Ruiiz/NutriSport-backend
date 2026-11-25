package com.nutrisport.asistente.dto;

import com.nutrisport.asistente.entidad.RolMensaje;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mensaje de una conversación con el asistente (§7.9).
 *
 * @param herramientasUsadas herramientas de solo lectura que consultó el asistente para responder (vacía en los
 *                           mensajes del usuario y en las respuestas seguras)
 * @param valoracion         −1, 1 o nulo (sin valorar); solo en las respuestas del asistente
 */
public record MensajeResponse(UUID id, RolMensaje rol, String contenido, List<String> herramientasUsadas,
        Integer valoracion, OffsetDateTime creadoEn) {
}
