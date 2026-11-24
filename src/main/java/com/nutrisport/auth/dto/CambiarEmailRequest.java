package com.nutrisport.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de email: se confirma con el enlace enviado a la dirección nueva. */
public record CambiarEmailRequest(
        @NotBlank(message = "El email nuevo es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 254, message = "El email no puede superar los 254 caracteres")
        String nuevoEmail,

        @NotBlank(message = "La contraseña es obligatoria")
        String password) {
}
