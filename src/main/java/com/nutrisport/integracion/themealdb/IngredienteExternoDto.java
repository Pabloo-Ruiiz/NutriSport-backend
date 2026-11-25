package com.nutrisport.integracion.themealdb;

/**
 * Ingrediente de una receta de TheMealDB, sin espacios sobrantes (docs/API_INTEGRATIONS.md §4.2).
 *
 * @param nombre {@code strIngredientN} tal cual, en inglés ("Chicken Breast")
 * @param medida {@code strMeasureN}, texto libre ("200g", "1 tbsp", "pinch"); nulo si no viene
 */
public record IngredienteExternoDto(String nombre, String medida) {
}
