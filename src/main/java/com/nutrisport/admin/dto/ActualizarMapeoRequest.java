package com.nutrisport.admin.dto;

import java.util.UUID;

/**
 * Emparejamiento fijado por el administrador (§7.10).
 *
 * @param alimentoId alimento público equivalente del catálogo; nulo para marcar que no hay equivalente
 */
public record ActualizarMapeoRequest(UUID alimentoId) {
}
