package com.nutrisport.recetas.dto;

import com.nutrisport.recetas.entidad.EstadoConversion;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ingrediente de una receta (§7.11).
 *
 * @param nombre           nombre del alimento emparejado del catálogo, o el original si no está emparejado
 * @param nombreOriginal   texto original ("Chicken Breast")
 * @param medidaOriginal   medida original ("200g", "1 tbsp"); nula si no hay
 * @param gramos           gramos para las raciones pedidas; nulo si la medida no se ha podido convertir
 * @param estadoConversion cómo se han obtenido los gramos
 * @param alimentoId       alimento emparejado; nulo si no lo está
 */
public record IngredienteRecetaResponse(String nombre, String nombreOriginal, String medidaOriginal,
        BigDecimal gramos, EstadoConversion estadoConversion, UUID alimentoId) {
}
