package com.nutrisport.asistente.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mensaje del usuario al asistente (POST /api/v1/asistente/conversaciones/{id}/mensajes, §7.9). */
public record EnviarMensajeRequest(
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
        String contenido) {
}
