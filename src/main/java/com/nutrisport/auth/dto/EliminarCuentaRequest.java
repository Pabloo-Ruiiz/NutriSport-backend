package com.nutrisport.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Eliminación de la cuenta (§6.3). Con {@code inmediata = true} se borran los datos al momento;
 * si no, la cuenta queda ELIMINADA y se borra físicamente a los 30 días.
 */
public record EliminarCuentaRequest(
        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        boolean inmediata) {
}
