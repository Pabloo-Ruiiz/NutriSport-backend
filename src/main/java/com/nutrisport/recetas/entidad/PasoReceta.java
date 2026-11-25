package com.nutrisport.recetas.entidad;

/**
 * Paso de la elaboración de una receta, guardado en la columna jsonb {@code receta.pasos}.
 *
 * @param orden posición del paso, desde 1
 * @param texto instrucción (en inglés en las recetas de TheMealDB)
 */
public record PasoReceta(int orden, String texto) {
}
