package com.nutrisport.comidas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un alimento de la comida: cantidad en {@code unidad}, que puede ser g, kg, ml, l (solo líquidos) o el
 * nombre de una porción casera del alimento ("unidad mediana").
 */
public record AlimentoRegistradoRequest(
        @NotNull(message = "El alimento es obligatorio")
        UUID alimentoId,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que 0")
        @DecimalMax(value = "5000", message = "La cantidad no puede superar 5000")
        @Digits(integer = 4, fraction = 2, message = "La cantidad admite como mucho dos decimales")
        BigDecimal cantidad,

        @NotBlank(message = "La unidad es obligatoria")
        @Size(max = 40, message = "La unidad no puede superar los 40 caracteres")
        String unidad) {
}
