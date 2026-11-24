package com.nutrisport.comun.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Energía (kcal) y macronutrientes (g). Inmutable: las operaciones devuelven un valor nuevo y no
 * redondean; {@link #redondear()} deja un decimal, como las columnas de la base de datos.
 */
public record Nutrientes(BigDecimal kcal, BigDecimal proteina, BigDecimal carbohidratos, BigDecimal grasa) {

    public static final Nutrientes CERO = new Nutrientes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO);

    private static final int DECIMALES = 1;

    public Nutrientes sumar(Nutrientes otros) {
        return new Nutrientes(kcal.add(otros.kcal), proteina.add(otros.proteina),
                carbohidratos.add(otros.carbohidratos), grasa.add(otros.grasa));
    }

    public Nutrientes escalar(BigDecimal factor) {
        return new Nutrientes(kcal.multiply(factor), proteina.multiply(factor), carbohidratos.multiply(factor),
                grasa.multiply(factor));
    }

    /** Un decimal con HALF_UP. */
    public Nutrientes redondear() {
        return new Nutrientes(kcal.setScale(DECIMALES, RoundingMode.HALF_UP),
                proteina.setScale(DECIMALES, RoundingMode.HALF_UP),
                carbohidratos.setScale(DECIMALES, RoundingMode.HALF_UP),
                grasa.setScale(DECIMALES, RoundingMode.HALF_UP));
    }
}
