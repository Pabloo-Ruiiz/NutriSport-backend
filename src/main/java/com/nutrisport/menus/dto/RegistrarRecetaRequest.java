package com.nutrisport.menus.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registrar como comida una receta del catálogo (POST /api/v1/comidas/desde-receta, §3.D): sus ingredientes
 * emparejados escalados a las raciones. La fecha puede ser como mucho mañana (lo comprueba el servicio).
 */
public record RegistrarRecetaRequest(
        @NotNull(message = "La receta es obligatoria")
        UUID recetaId,

        @NotNull(message = "Las raciones son obligatorias")
        @Positive(message = "Las raciones deben ser mayores que 0")
        @DecimalMax(value = "10", message = "Las raciones no pueden superar 10")
        @Digits(integer = 2, fraction = 2, message = "Las raciones admiten como mucho dos decimales")
        BigDecimal raciones,

        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        @NotNull(message = "El tipo de comida es obligatorio")
        TipoComida tipoComida) {
}
