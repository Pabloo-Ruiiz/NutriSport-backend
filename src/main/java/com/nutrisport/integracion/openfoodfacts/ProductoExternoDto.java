package com.nutrisport.integracion.openfoodfacts;

import com.nutrisport.catalogo.entidad.UnidadBase;
import java.math.BigDecimal;
import java.util.List;

/**
 * Producto de Open Food Facts traducido a nuestros nombres (docs/API_INTEGRATIONS.md §3.4), todavía sin
 * guardar. Los nutrientes son por 100 g o 100 ml según {@code unidadBase}; cada uno es {@code null} si el
 * proveedor no lo informa (nunca 0).
 *
 * @param codigo            código de barras ({@code code})
 * @param nombre            {@code product_name}; puede ser nulo
 * @param marca             {@code brands} (texto en v2, lista en Search-a-licious: se une con ", ")
 * @param cantidad          cantidad del envase tal cual ({@code quantity}, p. ej. "400 g")
 * @param unidadBase        G si {@code nutrition_data_per} es "100g" o no viene; ML si es "100ml"
 * @param racionGramos      {@code serving_quantity}
 * @param racionDescripcion {@code serving_size} (p. ej. "15 g")
 * @param alergenosTags     {@code allergens_tags} (p. ej. "en:milk"), sin traducir
 * @param trazasTags        {@code traces_tags}, sin traducir
 * @param categoriasTags    {@code categories_tags}, sin traducir
 * @param imagenUrl         {@code image_front_small_url}: se enlaza con atribución, no se copia
 */
public record ProductoExternoDto(
        String codigo,
        String nombre,
        String marca,
        String cantidad,
        BigDecimal kcal100,
        BigDecimal proteinas100,
        BigDecimal carbohidratos100,
        BigDecimal grasas100,
        BigDecimal azucares100,
        BigDecimal saturadas100,
        BigDecimal fibra100,
        BigDecimal sal100,
        UnidadBase unidadBase,
        BigDecimal racionGramos,
        String racionDescripcion,
        String ingredientesTexto,
        List<String> alergenosTags,
        List<String> trazasTags,
        List<String> categoriasTags,
        String imagenUrl) {

    public ProductoExternoDto {
        unidadBase = unidadBase != null ? unidadBase : UnidadBase.G;
        alergenosTags = alergenosTags == null ? List.of() : List.copyOf(alergenosTags);
        trazasTags = trazasTags == null ? List.of() : List.copyOf(trazasTags);
        categoriasTags = categoriasTags == null ? List.of() : List.copyOf(categoriasTags);
    }

    /**
     * Si trae energía y los tres macronutrientes: sin ellos el producto se muestra con el aviso "Información
     * nutricional incompleta" y no se puede añadir a una comida (§3.4).
     */
    public boolean nutricionCompleta() {
        return kcal100 != null && proteinas100 != null && carbohidratos100 != null && grasas100 != null;
    }
}
