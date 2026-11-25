package com.nutrisport.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ingrediente de una receta propia: un alimento público del catálogo con sus gramos para todas las raciones o, si
 * no se indican, una medida que se interpreta al calcular ("2 tbsp", "1 unidad").
 */
public record IngredienteRecetaAdminRequest(
        @NotNull(message = "El alimento es obligatorio")
        UUID alimentoId,

        @Positive(message = "Los gramos deben ser mayores que 0")
        @DecimalMax(value = "5000", message = "Un ingrediente no puede superar los 5000 g")
        @Digits(integer = 4, fraction = 1, message = "Los gramos admiten como mucho un decimal")
        BigDecimal gramos,

        @Size(max = 60, message = "La medida no puede superar los 60 caracteres")
        String medida) {

    /** Validación: hacen falta los gramos o la medida (el error se informa en el campo {@code cantidadIndicada}). */
    @JsonIgnore
    @AssertTrue(message = "Indica los gramos o la medida")
    public boolean isCantidadIndicada() {
        return gramos != null || (medida != null && !medida.isBlank());
    }
}
