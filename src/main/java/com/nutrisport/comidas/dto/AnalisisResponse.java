package com.nutrisport.comidas.dto;

import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.entidad.TipoAnalisis;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Estado y resultado de un análisis de foto (GET /api/v1/analisis/{id}, §7.4). El frontend lo consulta
 * hasta que el estado deja de ser PENDIENTE o PROCESANDO.
 *
 * @param totalEstimado suma de los nutrientes de la primera coincidencia de cada candidato
 */
public record AnalisisResponse(
        UUID id,
        TipoAnalisis tipo,
        EstadoAnalisis estado,
        TipoComida tipoComida,
        List<CandidatoAnalisis> candidatos,
        Nutrientes totalEstimado,
        String errorMensaje,
        OffsetDateTime creadoEn,
        OffsetDateTime completadoEn) {

    public AnalisisResponse {
        candidatos = candidatos == null ? List.of() : List.copyOf(candidatos);
    }
}
