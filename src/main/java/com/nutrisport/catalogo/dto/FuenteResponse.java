package com.nutrisport.catalogo.dto;

/**
 * Procedencia de un producto, para mostrar su atribución (docs/API_INTEGRATIONS.md §9).
 *
 * @param proveedor  OPEN_FOOD_FACTS, o la fuente del alimento del catálogo (BEDCA, ADMIN...)
 * @param url        ficha del producto en el proveedor; nula si no hay
 * @param licencia   p. ej. ODbL; nula si no aplica
 * @param atribucion texto que hay que mostrar junto al producto; nulo si no hace falta
 */
public record FuenteResponse(String proveedor, String url, String licencia, String atribucion) {
}
