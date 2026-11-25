package com.nutrisport.recetas.dto;

import java.math.BigDecimal;

/** Nutrientes por ración calculados a partir de los ingredientes (§4.9). */
public record NutricionRacion(BigDecimal kcal, BigDecimal proteinas, BigDecimal carbohidratos, BigDecimal grasas) {
}
