package com.nutrisport.perfil.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Objetivos fijados a mano por el usuario (PUT /api/v1/perfil/objetivos, §7.2), por ejemplo los de su
 * nutricionista. Mientras estén activos no se recalculan. Las columnas son numeric(7,1).
 */
public record ObjetivosManualesRequest(
        @NotNull(message = "La energía es obligatoria")
        @DecimalMin(value = "1000", message = "La energía debe estar entre 1000 y 6000 kcal")
        @DecimalMax(value = "6000", message = "La energía debe estar entre 1000 y 6000 kcal")
        @Digits(integer = 4, fraction = 1, message = "La energía admite como mucho un decimal")
        BigDecimal kcal,

        @NotNull(message = "La proteína es obligatoria")
        @DecimalMin(value = "30", message = "La proteína debe estar entre 30 y 400 g")
        @DecimalMax(value = "400", message = "La proteína debe estar entre 30 y 400 g")
        @Digits(integer = 3, fraction = 1, message = "La proteína admite como mucho un decimal")
        BigDecimal proteinaG,

        @NotNull(message = "Los carbohidratos son obligatorios")
        @DecimalMin(value = "0", message = "Los carbohidratos deben estar entre 0 y 900 g")
        @DecimalMax(value = "900", message = "Los carbohidratos deben estar entre 0 y 900 g")
        @Digits(integer = 3, fraction = 1, message = "Los carbohidratos admiten como mucho un decimal")
        BigDecimal carbohidratosG,

        @NotNull(message = "La grasa es obligatoria")
        @DecimalMin(value = "20", message = "La grasa debe estar entre 20 y 300 g")
        @DecimalMax(value = "300", message = "La grasa debe estar entre 20 y 300 g")
        @Digits(integer = 3, fraction = 1, message = "La grasa admite como mucho un decimal")
        BigDecimal grasaG) {
}
