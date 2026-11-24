package com.nutrisport.seguimiento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registra el pesaje de un día (POST /api/v1/seguimiento/peso, §7.7); si ya había uno ese día, lo
 * sustituye. La fecha no puede ser futura (lo comprueba el servicio con el reloj de la aplicación).
 */
public record PesoRequest(
        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "30", message = "El peso debe estar entre 30 y 250 kg")
        @DecimalMax(value = "250", message = "El peso debe estar entre 30 y 250 kg")
        @Digits(integer = 3, fraction = 2, message = "El peso admite como mucho dos decimales")
        BigDecimal pesoKg) {
}
