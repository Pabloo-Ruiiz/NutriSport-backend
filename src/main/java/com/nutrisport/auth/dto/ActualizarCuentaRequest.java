package com.nutrisport.auth.dto;

import com.nutrisport.auth.entidad.Unidades;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Actualización parcial de la cuenta (PATCH): los campos nulos no se modifican. */
public record ActualizarCuentaRequest(
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        Unidades unidades) {
}
