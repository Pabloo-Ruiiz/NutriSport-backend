package com.nutrisport.catalogo.servicio;

import java.math.BigDecimal;

/**
 * Medida de un ingrediente interpretada ("2 tblsp" → 2, "tbsp", VOLUMEN).
 *
 * @param cantidad número de unidades (1 si la medida no lleva número; 0 en las despreciables; nula si es
 *                 DESCONOCIDA)
 * @param unidad   unidad canónica: g, kg, oz, lb, ml, l, cup, tbsp, tsp, "unidad" (número solo) o la palabra de la
 *                 medida ("chopped", "clove", "handful", "pinch"...); nula si es DESCONOCIDA
 * @param tipo     clase de la medida
 */
public record MedidaInterpretada(BigDecimal cantidad, String unidad, TipoMedida tipo) {

    static final MedidaInterpretada DESCONOCIDA = new MedidaInterpretada(null, null, TipoMedida.DESCONOCIDA);
}
