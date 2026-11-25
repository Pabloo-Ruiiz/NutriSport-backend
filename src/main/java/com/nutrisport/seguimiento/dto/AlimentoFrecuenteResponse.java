package com.nutrisport.seguimiento.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uno de los alimentos más consumidos del periodo.
 *
 * @param veces             en cuántas líneas de comida aparece
 * @param porcentajeEnergia kcal que ha aportado / kcal consumidas en el periodo × 100, con un decimal
 * @param fuente            de dónde salen sus datos (BEDCA, USDA, OPEN_FOOD_FACTS…)
 */
public record AlimentoFrecuenteResponse(UUID alimentoId, String nombre, int veces, BigDecimal porcentajeEnergia,
        String fuente) {
}
