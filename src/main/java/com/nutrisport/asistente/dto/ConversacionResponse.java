package com.nutrisport.asistente.dto;

import java.util.List;
import java.util.UUID;

/**
 * Conversación con todos sus mensajes en orden cronológico (§7.9).
 *
 * @param titulo nulo hasta el primer mensaje
 */
public record ConversacionResponse(UUID id, String titulo, boolean archivada, List<MensajeResponse> mensajes) {
}
