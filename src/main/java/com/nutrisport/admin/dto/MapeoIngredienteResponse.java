package com.nutrisport.admin.dto;

import com.nutrisport.catalogo.entidad.MetodoMapeo;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Emparejamiento de un ingrediente en la cola de revisión del administrador (§7.10).
 *
 * @param nombreNormalizado   nombre del ingrediente en minúsculas y sin tildes ("chicken breast")
 * @param alimentoId          alimento emparejado; nulo si no hay
 * @param confianza           similitud de texto del emparejamiento automático (0–1); nula si es manual o no la hay
 * @param recetasRecalculadas recetas recalculadas al cambiarlo; nulo en los listados
 */
public record MapeoIngredienteResponse(UUID id, String nombreNormalizado, UUID alimentoId, String alimentoNombre,
        MetodoMapeo metodo, BigDecimal confianza, boolean revisado, Integer recetasRecalculadas) {
}
