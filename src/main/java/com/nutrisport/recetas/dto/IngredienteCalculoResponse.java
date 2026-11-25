package com.nutrisport.recetas.dto;

import com.nutrisport.recetas.entidad.EstadoConversion;
import java.math.BigDecimal;

/**
 * Un ingrediente en el desglose del cálculo nutricional (§7.3, GET /recetas/{id}/nutricion).
 *
 * @param nombre           alimento emparejado, o el nombre original si no lo está
 * @param gramos           gramos para las raciones pedidas; nulo si la medida no se ha podido convertir
 * @param estadoConversion EXACTA, ESTIMADA, DESPRECIABLE o SIN_DATOS
 * @param fuente           procedencia de los nutrientes (USDA, BEDCA, ADMIN, OPEN_FOOD_FACTS...); nula si no está emparejado
 */
public record IngredienteCalculoResponse(String nombre, String nombreOriginal, String medidaOriginal,
        BigDecimal gramos, EstadoConversion estadoConversion, String fuente) {
}
