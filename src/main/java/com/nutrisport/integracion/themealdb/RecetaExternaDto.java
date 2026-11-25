package com.nutrisport.integracion.themealdb;

import java.util.List;

/**
 * Receta completa de TheMealDB (búsqueda por nombre y detalle, docs/API_INTEGRATIONS.md §4.1), en inglés y sin
 * nutrientes, raciones ni alérgenos (§4.4).
 *
 * @param idMeal        identificador en TheMealDB
 * @param nombre        {@code strMeal}
 * @param categoria     {@code strCategory} original ("Chicken")
 * @param area          {@code strArea} ("Moroccan")
 * @param instrucciones {@code strInstructions}
 * @param imagenUrl     {@code strMealThumb}: se enlaza con atribución, no se copia
 * @param etiquetas     {@code strTags} separadas por comas; vacía si no hay
 * @param youtube       {@code strYoutube}; nulo si no hay
 * @param ingredientes  parejas {@code strIngredientN}/{@code strMeasureN} (1..20) con ingrediente, en su orden
 */
public record RecetaExternaDto(
        String idMeal,
        String nombre,
        String categoria,
        String area,
        String instrucciones,
        String imagenUrl,
        List<String> etiquetas,
        String youtube,
        List<IngredienteExternoDto> ingredientes) {

    public RecetaExternaDto {
        etiquetas = etiquetas == null ? List.of() : List.copyOf(etiquetas);
        ingredientes = ingredientes == null ? List.of() : List.copyOf(ingredientes);
    }
}
