package com.nutrisport.seguimiento.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Periodo actual frente al anterior (GET /api/v1/seguimiento/comparativa, §3.F y §7.7). Las estadísticas van
 * resumidas: sin serie.
 *
 * @param actual       desde el primer día de la semana (lunes) o del mes actual hasta hoy
 * @param anterior     la semana o el mes anterior completos
 * @param variacionPct variación de la media diaria respecto al periodo anterior en % con un decimal ("kcal",
 *                     "proteina", "carbohidratos", "grasa"; nula si el anterior es 0) y de la adherencia en puntos
 *                     ("adherenciaPts")
 */
public record ComparativaResponse(EstadisticasResponse actual, EstadisticasResponse anterior,
        Map<String, BigDecimal> variacionPct) {
}
