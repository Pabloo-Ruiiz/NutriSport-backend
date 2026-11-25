package com.nutrisport.recetas.dto;

import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import java.util.List;

/**
 * Desglose del cálculo nutricional de una receta (§7.3, docs/API_INTEGRATIONS.md §7). Las cantidades son para
 * {@code raciones}; los nutrientes, por ración.
 *
 * @param raciones          raciones de las cantidades (las pedidas o las de la receta)
 * @param racionesSupuestas {@code true} si las raciones de la receta son supuestas (4 en TheMealDB)
 * @param porRacion         nulo si el cálculo es INCOMPLETO
 * @param completitudPct    % de la masa resuelta (los ingredientes despreciables no cuentan)
 */
public record NutricionRecetaResponse(int raciones, boolean racionesSupuestas, Nutrientes porRacion,
        EstadoNutricional estado, int completitudPct, List<IngredienteCalculoResponse> ingredientes) {

    public NutricionRecetaResponse {
        ingredientes = List.copyOf(ingredientes);
    }
}
