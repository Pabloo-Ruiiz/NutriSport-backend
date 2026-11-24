package com.nutrisport.catalogo.entidad;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Medida casera de un alimento, guardada en la columna jsonb {@code porciones}. Se usa también en la
 * API, por eso lleva las validaciones.
 *
 * @param nombre nombre de la medida, p. ej. "unidad mediana" o "cucharada"
 * @param gramos gramos (o mililitros, según la unidad base) que equivale, p. ej. 120
 */
public record PorcionCasera(
        @NotBlank(message = "El nombre de la porción es obligatorio")
        @Size(max = 40, message = "El nombre de la porción no puede superar los 40 caracteres")
        String nombre,

        @NotNull(message = "Los gramos de la porción son obligatorios")
        @Positive(message = "Los gramos de la porción deben ser mayores que 0")
        @DecimalMax(value = "5000", message = "Una porción no puede superar los 5000 g")
        BigDecimal gramos) {
}
