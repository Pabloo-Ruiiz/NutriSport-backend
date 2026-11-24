package com.nutrisport.auth.dto;

import com.nutrisport.comun.validacion.ContrasenaSegura;
import jakarta.validation.constraints.NotBlank;

public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String passwordActual,

        @NotBlank(message = "La contraseña nueva es obligatoria")
        @ContrasenaSegura
        String nuevaPassword) {
}
