package com.nutrisport.compra.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Ingredientes de una receta para la lista (POST /api/v1/listas-compra/{id}/productos/desde-receta, §7.6). */
public record AnadirRecetaListaRequest(
        @NotNull(message = "La receta es obligatoria")
        UUID recetaId,

        @NotNull(message = "Las raciones son obligatorias")
        @Positive(message = "Las raciones deben ser mayores que 0")
        @DecimalMax(value = "50", message = "Las raciones no pueden superar 50")
        @Digits(integer = 2, fraction = 2, message = "Las raciones admiten como mucho dos decimales")
        BigDecimal raciones) {
}
