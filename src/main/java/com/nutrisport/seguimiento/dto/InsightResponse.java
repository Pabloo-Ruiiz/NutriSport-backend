package com.nutrisport.seguimiento.dto;

import com.nutrisport.seguimiento.entidad.SeveridadInsight;
import com.nutrisport.seguimiento.entidad.TipoInsight;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Patrón detectado (GET /api/v1/seguimiento/insights, §4.6). El texto lleva el diagnóstico con las cifras calculadas
 * por las reglas y un consejo.
 *
 * @param periodoDesde primer día analizado
 * @param periodoHasta último día analizado
 */
public record InsightResponse(UUID id, TipoInsight tipo, SeveridadInsight severidad, String titulo, String texto,
        LocalDate periodoDesde, LocalDate periodoHasta, OffsetDateTime creadoEn) {
}
