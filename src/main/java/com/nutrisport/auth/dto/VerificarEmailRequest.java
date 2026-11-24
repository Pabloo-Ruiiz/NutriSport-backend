package com.nutrisport.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Token recibido en el enlace del email de verificación. */
public record VerificarEmailRequest(
        @NotBlank(message = "El token es obligatorio")
        String token) {
}
