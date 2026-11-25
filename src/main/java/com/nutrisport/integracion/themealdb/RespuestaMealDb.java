package com.nutrisport.integracion.themealdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Traduce las respuestas de TheMealDB (un {@code Map} leído del JSON) a nuestros DTO. Solo lee los campos de
 * docs/API_INTEGRATIONS.md §4. Tolera {@code {"meals": null}} (lista vacía), los huecos de ingredientes con
 * {@code ""} o {@code null} y los espacios sobrantes en las medidas ("2 tblsp "). Nunca lanza excepciones.
 */
final class RespuestaMealDb {

    /** TheMealDB tiene como mucho 20 parejas strIngredientN/strMeasureN. */
    static final int MAXIMO_INGREDIENTES = 20;

    private RespuestaMealDb() {
    }

    /** Elementos de {@code meals}; vacío si es {@code null}, no existe o no es una lista. */
    static List<Map<?, ?>> elementos(Map<String, Object> cuerpo, String clave) {
        if (cuerpo == null || !(cuerpo.get(clave) instanceof List<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .filter(elemento -> elemento instanceof Map<?, ?>)
                .<Map<?, ?>>map(elemento -> (Map<?, ?>) elemento)
                .toList();
    }

    /** Receta completa (search.php y lookup.php); nula si no trae idMeal o strMeal. */
    static RecetaExternaDto aReceta(Map<?, ?> receta) {
        String idMeal = texto(receta.get("idMeal"));
        String nombre = texto(receta.get("strMeal"));
        if (idMeal == null || nombre == null) {
            return null;
        }
        return new RecetaExternaDto(
                idMeal,
                nombre,
                texto(receta.get("strCategory")),
                texto(receta.get("strArea")),
                texto(receta.get("strInstructions")),
                texto(receta.get("strMealThumb")),
                etiquetas(texto(receta.get("strTags"))),
                texto(receta.get("strYoutube")),
                ingredientes(receta));
    }

    /** Resultado de filter.php (solo id, nombre e imagen); nulo si no trae idMeal o strMeal. */
    static RecetaResumenExternaDto aResumen(Map<?, ?> receta) {
        String idMeal = texto(receta.get("idMeal"));
        String nombre = texto(receta.get("strMeal"));
        if (idMeal == null || nombre == null) {
            return null;
        }
        return new RecetaResumenExternaDto(idMeal, nombre, texto(receta.get("strMealThumb")));
    }

    /** Categoría de categories.php; nula si no trae strCategory. */
    static CategoriaExternaDto aCategoria(Map<?, ?> categoria) {
        String nombre = texto(categoria.get("strCategory"));
        if (nombre == null) {
            return null;
        }
        return new CategoriaExternaDto(texto(categoria.get("idCategory")), nombre,
                texto(categoria.get("strCategoryThumb")), texto(categoria.get("strCategoryDescription")));
    }

    /**
     * Parejas strIngredientN/strMeasureN de 1 a 20 con ingrediente, en su orden. Los huecos ("" o null) se
     * ignoran; la medida vacía queda nula.
     */
    static List<IngredienteExternoDto> ingredientes(Map<?, ?> receta) {
        List<IngredienteExternoDto> ingredientes = new ArrayList<>();
        for (int i = 1; i <= MAXIMO_INGREDIENTES; i++) {
            String nombre = texto(receta.get("strIngredient" + i));
            if (nombre != null) {
                ingredientes.add(new IngredienteExternoDto(nombre, texto(receta.get("strMeasure" + i))));
            }
        }
        return ingredientes;
    }

    /** "Meat,Casserole" → [Meat, Casserole]. */
    static List<String> etiquetas(String strTags) {
        if (strTags == null) {
            return List.of();
        }
        return Arrays.stream(strTags.split(","))
                .map(String::strip)
                .filter(etiqueta -> !etiqueta.isEmpty())
                .toList();
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

    static List<String> textos(List<Map<?, ?>> elementos, String clave) {
        return elementos.stream().map(elemento -> texto(elemento.get(clave))).filter(Objects::nonNull).toList();
    }
}
