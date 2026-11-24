package com.nutrisport.actividad.servicio;

import com.nutrisport.actividad.entidad.Intensidad;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Gasto energético <em>neto</em> de un entrenamiento (§3.G), con programación convencional:
 * {@code kcal = (MET − 1) × peso (kg) × horas × factor de intensidad}. Se resta 1 MET porque el gasto
 * en reposo de ese tiempo ya está incluido en el metabolismo basal. Factores: BAJA 0,85 · MEDIA 1,00 ·
 * ALTA 1,15 (el RPE es informativo y no interviene). Es una estimación.
 */
@Component
public class CalculadoraGasto {

    private static final BigDecimal MINUTOS_POR_HORA = new BigDecimal("60");

    /** Kcal netas redondeadas a entero (HALF_UP); nunca negativas aunque el MET sea menor que 1. */
    public BigDecimal kcalNetas(BigDecimal met, BigDecimal pesoKg, int minutos, Intensidad intensidad) {
        BigDecimal metNeto = met.subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
        return metNeto
                .multiply(pesoKg)
                .multiply(BigDecimal.valueOf(minutos))
                .multiply(factor(intensidad))
                .divide(MINUTOS_POR_HORA, 0, RoundingMode.HALF_UP);
    }

    static BigDecimal factor(Intensidad intensidad) {
        return switch (intensidad) {
            case BAJA -> new BigDecimal("0.85");
            case MEDIA -> BigDecimal.ONE;
            case ALTA -> new BigDecimal("1.15");
        };
    }
}
