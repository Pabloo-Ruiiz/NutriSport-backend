package com.nutrisport.menus.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sugerencia de la IA ya validada (§4.5, marca "Sugerencia IA"). Las cifras las calcula el backend con el catálogo,
 * nunca el modelo.
 *
 * @param id                  para aplicarla (POST .../sugerencia-ia/aplicar)
 * @param texto               el cambio y su efecto, o el motivo por el que se descarta
 * @param deltaKcalRacion     cambio de kcal por ración de la receta; nulo si no es válida
 * @param deltaProteinaRacion cambio de proteína (g) por ración; nulo si no es válida
 * @param valida              si cumple las restricciones y el alimento existe en el catálogo
 */
public record SugerenciaIaResponse(UUID id, String texto, BigDecimal deltaKcalRacion, BigDecimal deltaProteinaRacion,
        boolean valida) {
}
