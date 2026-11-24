package com.nutrisport.auth.dto;

import com.nutrisport.comun.validacion.ContrasenaSegura;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de cuenta (§3.A). Las tres declaraciones son booleanos primitivos: si faltan en el JSON
 * valen {@code false} y {@code @AssertTrue} las rechaza.
 */
public record RegistroRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String nombre,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 254, message = "El email no puede superar los 254 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @ContrasenaSegura
        String password,

        @AssertTrue(message = "Debes declarar que eres mayor de edad")
        boolean mayorDeEdad,

        @AssertTrue(message = "Debes aceptar los términos de uso")
        boolean aceptaTerminos,

        @AssertTrue(message = "Debes dar tu consentimiento para el tratamiento de datos de salud")
        boolean consentimientoSalud) {
}
