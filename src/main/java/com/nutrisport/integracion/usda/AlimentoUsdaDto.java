package com.nutrisport.integracion.usda;

import java.math.BigDecimal;

/**
 * Alimento genérico de USDA FoodData Central (Foundation o SR Legacy, docs/API_INTEGRATIONS.md §5), con la
 * energía y los tres macronutrientes. Cada nutriente es nulo si USDA no lo informa.
 * <p>
 * Los valores se toman como por 100 g: §5.2 indica que los alimentos Foundation usan medidas de 100 unidades y
 * que los de SR Legacy son coherentes con 100 g, pero la guía de la API no lo dice expresamente (a verificar).
 *
 * @param fdcId            identificador en FoodData Central
 * @param descripcion      nombre en inglés ("Bananas, raw")
 * @param tipoDatos        "Foundation" o "SR Legacy"
 * @param kcal100          nutriente 1008 en KCAL (no el de kJ)
 * @param proteinas100     nutriente 1003
 * @param carbohidratos100 nutriente 1005 (por diferencia)
 * @param grasas100        nutriente 1004
 */
public record AlimentoUsdaDto(String fdcId, String descripcion, String tipoDatos, BigDecimal kcal100,
        BigDecimal proteinas100, BigDecimal carbohidratos100, BigDecimal grasas100) {

    /** Si trae energía y los tres macronutrientes. */
    public boolean nutricionCompleta() {
        return kcal100 != null && proteinas100 != null && carbohidratos100 != null && grasas100 != null;
    }
}
