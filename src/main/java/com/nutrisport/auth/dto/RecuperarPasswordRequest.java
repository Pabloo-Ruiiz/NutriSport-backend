package com.nutrisport.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Solicitud del email de recuperación de contraseña. */
public record RecuperarPasswordRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        String email) {
}
