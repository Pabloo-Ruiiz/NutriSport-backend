package com.nutrisport.integracion.themealdb;

/**
 * Categoría de TheMealDB ({@code categories.php}).
 *
 * @param id          {@code idCategory}
 * @param nombre      {@code strCategory} ("Chicken"), la clave de {@link TablaCategoriasMealDb}
 * @param imagenUrl   {@code strCategoryThumb}
 * @param descripcion {@code strCategoryDescription}, en inglés
 */
public record CategoriaExternaDto(String id, String nombre, String imagenUrl, String descripcion) {
}
