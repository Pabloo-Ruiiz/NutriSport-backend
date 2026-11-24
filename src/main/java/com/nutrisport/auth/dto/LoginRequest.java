package com.nutrisport.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inicio de sesión. {@code mantenerSesion} = "Mantener la sesión iniciada": la cookie del refresh
 * token dura 7 días; si es {@code false}, es una cookie de sesión.
 */
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        boolean mantenerSesion) {
}
