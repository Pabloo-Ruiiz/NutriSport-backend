package com.nutrisport.asistente.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Valoración de una respuesta del asistente (POST /api/v1/asistente/mensajes/{id}/valoracion, §7.9): −1 o 1. */
public record ValoracionRequest(
        @NotNull(message = "La valoración es obligatoria")
        Integer valor) {

    /** Validación: solo −1 (no útil) o 1 (útil); el error se informa en el campo {@code valorValido}. */
    @JsonIgnore
    @AssertTrue(message = "La valoración debe ser -1 o 1")
    public boolean isValorValido() {
        return valor == null || valor == -1 || valor == 1;
    }
}
