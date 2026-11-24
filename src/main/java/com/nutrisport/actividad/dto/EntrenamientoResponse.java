package com.nutrisport.actividad.dto;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Intensidad;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Entrenamiento con su gasto neto estimado (§3.G). */
public record EntrenamientoResponse(
        UUID id,
        String tipoActividadCodigo,
        String tipoActividadNombre,
        OffsetDateTime fechaHora,
        int duracionMin,
        Intensidad intensidad,
        Integer rpe,
        EstadoEntrenamiento estado,
        BigDecimal kcalEstimadas,
        BigDecimal distanciaKm,
        String notas) {
}
