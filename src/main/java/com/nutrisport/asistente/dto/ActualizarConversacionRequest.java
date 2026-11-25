package com.nutrisport.asistente.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Renombrar o archivar una conversación (PATCH /api/v1/asistente/conversaciones/{id}, §7.9). Nulos = sin cambios. */
public record ActualizarConversacionRequest(
        @Size(max = 100, message = "El título no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El título no puede estar vacío")
        String titulo,

        Boolean archivada) {
}
