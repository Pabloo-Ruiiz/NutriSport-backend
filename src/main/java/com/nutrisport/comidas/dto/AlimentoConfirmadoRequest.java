package com.nutrisport.comidas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Alimento que el usuario confirma tras revisar un análisis de foto. */
public record AlimentoConfirmadoRequest(
        @NotNull(message = "El alimento es obligatorio")
        UUID alimentoId,

        @NotNull(message = "Los gramos son obligatorios")
        @Positive(message = "Los gramos deben ser mayores que 0")
        @DecimalMax(value = "5000", message = "Los gramos no pueden superar 5000")
        BigDecimal gramos,

        @PositiveOrZero(message = "El índice del candidato no puede ser negativo")
        Integer indiceCandidato) {
}
