package com.nutrisport.actividad.dto;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Intensidad;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Registra, planifica o edita un entrenamiento (POST y PUT /api/v1/entrenamientos, §7.8). Si no se
 * indica el estado: al crear, PLANIFICADO si la fecha es futura y REALIZADO si no; al editar, se
 * conserva el que tenía.
 */
public record EntrenamientoRequest(
        @NotBlank(message = "El tipo de actividad es obligatorio")
        @Size(max = 40, message = "El tipo de actividad no puede superar los 40 caracteres")
        String tipoActividadCodigo,

        @NotNull(message = "La fecha y hora son obligatorias")
        OffsetDateTime fechaHora,

        @NotNull(message = "La duración es obligatoria")
        @Min(value = 1, message = "La duración debe estar entre 1 y 600 minutos")
        @Max(value = 600, message = "La duración debe estar entre 1 y 600 minutos")
        Integer duracionMin,

        @NotNull(message = "La intensidad es obligatoria")
        Intensidad intensidad,

        @Min(value = 1, message = "El RPE debe estar entre 1 y 10")
        @Max(value = 10, message = "El RPE debe estar entre 1 y 10")
        Integer rpe,

        EstadoEntrenamiento estado,

        @DecimalMin(value = "0", message = "La distancia no puede ser negativa")
        @DecimalMax(value = "9999.99", message = "La distancia no puede superar 9999,99 km")
        @Digits(integer = 4, fraction = 2, message = "La distancia admite como mucho dos decimales")
        BigDecimal distanciaKm,

        @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
        String notas) {
}
