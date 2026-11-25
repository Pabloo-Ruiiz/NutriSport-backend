package com.nutrisport.recetas.dto;

import com.nutrisport.catalogo.dto.FuenteResponse;
import com.nutrisport.recetas.entidad.OrigenReceta;
import java.util.UUID;

/**
 * Receta en un listado (búsqueda, relacionadas, favoritas). Una receta de TheMealDB aún no importada no tiene
 * {@code id}: lleva {@code referenciaExterna} (idMeal) y {@code estadoNutricional = PENDIENTE}; su detalle se
 * pide con {@code GET /recetas/externas/themealdb/{idMeal}}, que la importa.
 *
 * @param estadoNutricional COMPLETO, APROXIMADO, INCOMPLETO o PENDIENTE (no importada)
 * @param nutricionRacion   nula si el cálculo no es COMPLETO ni APROXIMADO
 * @param compatible        si encaja con las restricciones estrictas del usuario ({@code false} si no se sabe)
 */
public record RecetaResumenResponse(
        UUID id,
        String referenciaExterna,
        OrigenReceta origen,
        String nombre,
        String categoria,
        String area,
        String imagenUrl,
        NutricionRacion nutricionRacion,
        String estadoNutricional,
        boolean alergenosVerificados,
        boolean compatible,
        boolean favorita,
        FuenteResponse fuente) {
}
