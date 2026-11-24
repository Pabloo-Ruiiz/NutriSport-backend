package com.nutrisport.integracion.openfoodfacts;

import com.nutrisport.catalogo.entidad.UnidadBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Traduce un producto de la respuesta de Open Food Facts (un {@code Map} leído del JSON) a
 * {@link ProductoExternoDto}. Solo lee los campos de docs/API_INTEGRATIONS.md §3.1 y tolera las diferencias
 * entre la API v2 y Search-a-licious (p. ej. {@code brands} como texto o como lista) y los valores que llegan
 * como texto en lugar de como número. Un campo ausente o ilegible queda nulo; nunca lanza excepciones.
 */
final class RespuestaOff {

    private RespuestaOff() {
    }

    /**
     * @param producto       objeto {@code product} (v2) o un elemento de {@code hits} (Search-a-licious)
     * @param codigoPorDefecto código pedido, si el objeto no trae {@code code}
     */
    static ProductoExternoDto aProducto(Map<?, ?> producto, String codigoPorDefecto) {
        Map<?, ?> nutrientes = producto.get("nutriments") instanceof Map<?, ?> mapa ? mapa : Map.of();
        String codigo = texto(producto.get("code"));
        return new ProductoExternoDto(
                codigo != null ? codigo : codigoPorDefecto,
                texto(producto.get("product_name")),
                marca(producto.get("brands")),
                texto(producto.get("quantity")),
                decimal(nutrientes.get("energy-kcal_100g")),
                decimal(nutrientes.get("proteins_100g")),
                decimal(nutrientes.get("carbohydrates_100g")),
                decimal(nutrientes.get("fat_100g")),
                decimal(nutrientes.get("sugars_100g")),
                decimal(nutrientes.get("saturated-fat_100g")),
                decimal(nutrientes.get("fiber_100g")),
                decimal(nutrientes.get("salt_100g")),
                unidad(texto(producto.get("nutrition_data_per"))),
                decimal(producto.get("serving_quantity")),
                texto(producto.get("serving_size")),
                texto(producto.get("ingredients_text")),
                etiquetas(producto.get("allergens_tags")),
                etiquetas(producto.get("traces_tags")),
                etiquetas(producto.get("categories_tags")),
                texto(producto.get("image_front_small_url")));
    }

    /** Texto sin espacios sobrantes; nulo si no es texto (o número) o está vacío. */
    static String texto(Object valor) {
        if (valor instanceof String cadena) {
            return cadena.isBlank() ? null : cadena.strip();
        }
        if (valor instanceof Number numero) {
            return numero.toString();
        }
        return null;
    }

    /** {@code brands}: texto en la API v2 y lista en Search-a-licious; la lista se une con ", ". */
    static String marca(Object valor) {
        if (valor instanceof List<?> lista) {
            List<String> marcas = lista.stream().map(RespuestaOff::texto).filter(Objects::nonNull).toList();
            return marcas.isEmpty() ? null : String.join(", ", marcas);
        }
        return texto(valor);
    }

    /** Número JSON o texto numérico (con punto o coma decimal); nulo si no se puede leer. */
    static BigDecimal decimal(Object valor) {
        try {
            if (valor instanceof Number numero) {
                return new BigDecimal(numero.toString());
            }
            if (valor instanceof String cadena && !cadena.isBlank()) {
                return new BigDecimal(cadena.strip().replace(',', '.'));
            }
        } catch (NumberFormatException e) {
            // "traces", "<0.5"...: dato no disponible
        }
        return null;
    }

    /** "100ml" → ML; "100g", ausente o cualquier otro valor → G (§3.4). */
    static UnidadBase unidad(String nutritionDataPer) {
        return nutritionDataPer != null && nutritionDataPer.toLowerCase(Locale.ROOT).replace(" ", "").equals("100ml")
                ? UnidadBase.ML
                : UnidadBase.G;
    }

    /** Lista de etiquetas en texto; los elementos que no son texto se descartan. */
    static List<String> etiquetas(Object valor) {
        if (!(valor instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream().map(RespuestaOff::texto).filter(Objects::nonNull).toList();
    }
}
