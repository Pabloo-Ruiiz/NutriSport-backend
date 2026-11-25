package com.nutrisport.menus.dto;

import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import java.math.BigDecimal;

/**
 * Receta propuesta para una comida del menú con las raciones que mejor ajustan (§3.D y §7.5): una alternativa a la
 * receta de una comida o una receta disponible para un hueco del día.
 *
 * @param nutrientes        los de una ración × raciones
 * @param diferenciaKcalPct (kcal propuestas − kcal de referencia) / kcal de referencia × 100, con un decimal: la
 *                          referencia es la comida original (alternativas) o la parte del hueco del día que corresponde
 *                          a la franja (recetas disponibles); nula si no hay referencia (sin perfil)
 */
public record AlternativaResponse(RecetaResumenResponse receta, BigDecimal raciones, Nutrientes nutrientes,
        BigDecimal diferenciaKcalPct) {
}
