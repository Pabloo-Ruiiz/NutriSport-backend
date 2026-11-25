package com.nutrisport.integracion.themealdb;

/**
 * Resultado de los filtros de TheMealDB ({@code filter.php}), que solo devuelven el identificador, el nombre y
 * la imagen (docs/API_INTEGRATIONS.md §4.1): para los ingredientes hay que pedir el detalle.
 */
public record RecetaResumenExternaDto(String idMeal, String nombre, String imagenUrl) {
}
