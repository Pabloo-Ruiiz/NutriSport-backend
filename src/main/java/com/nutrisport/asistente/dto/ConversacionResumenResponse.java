package com.nutrisport.asistente.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conversación en el listado (§7.9).
 *
 * @param titulo          nulo hasta el primer mensaje
 * @param ultimoMensajeEn nulo si aún no tiene mensajes
 * @param vistaPrevia     el comienzo del último mensaje (como mucho 120 caracteres); nula sin mensajes
 */
public record ConversacionResumenResponse(UUID id, String titulo, boolean archivada, OffsetDateTime ultimoMensajeEn,
        String vistaPrevia) {
}
