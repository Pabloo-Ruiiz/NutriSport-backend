package com.nutrisport.integracion.usda;

import java.math.BigDecimal;

/**
 * Medida casera de un alimento de USDA ({@code foodPortions} del detalle), p. ej. 1 "cup, sliced" = 150 g.
 *
 * @param modifier   descripción de la medida ({@code modifier}; si no viene, {@code portionDescription} o el nombre
 *                   de {@code measureUnit})
 * @param amount     cuántas medidas pesan {@code gramWeight} (1 si no viene)
 * @param gramWeight gramos de {@code amount} medidas
 */
public record PorcionUsdaDto(String modifier, BigDecimal amount, BigDecimal gramWeight) {
}
