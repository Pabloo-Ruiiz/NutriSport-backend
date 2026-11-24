package com.nutrisport.auth.dto;

import com.nutrisport.comun.validacion.ContrasenaSegura;
import jakarta.validation.constraints.NotBlank;

/** Nueva contraseña con el token del enlace de recuperación. */
public record RestablecerPasswordRequest(
        @NotBlank(message = "El token es obligatorio")
        String token,

        @NotBlank(message = "La contraseña es obligatoria")
        @ContrasenaSegura
        String nuevaPassword) {
}
