package com.nutrisport.perfil.dto;

import com.nutrisport.comun.validacion.MayorDeEdad;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Crea o sustituye el perfil deportivo (PUT /api/v1/perfil, §7.2). Los rangos son los de los CHECK
 * de V3. {@code fechaCompeticion} es obligatoria si el objetivo es {@code COMPETICION} (lo comprueba
 * el servicio). Los objetivos nutricionales no se envían aquí.
 */
public record PerfilRequest(
        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        @MayorDeEdad
        LocalDate fechaNacimiento,

        @NotNull(message = "El sexo es obligatorio")
        Sexo sexo,

        @NotNull(message = "La altura es obligatoria")
        @DecimalMin(value = "120", message = "La altura debe estar entre 120 y 230 cm")
        @DecimalMax(value = "230", message = "La altura debe estar entre 120 y 230 cm")
        @Digits(integer = 3, fraction = 1, message = "La altura admite como mucho un decimal")
        BigDecimal alturaCm,

        @NotNull(message = "El peso es obligatorio")
        @DecimalMin(value = "30", message = "El peso debe estar entre 30 y 250 kg")
        @DecimalMax(value = "250", message = "El peso debe estar entre 30 y 250 kg")
        @Digits(integer = 3, fraction = 2, message = "El peso admite como mucho dos decimales")
        BigDecimal pesoKg,

        @DecimalMin(value = "3", message = "El porcentaje de grasa debe estar entre 3 y 60")
        @DecimalMax(value = "60", message = "El porcentaje de grasa debe estar entre 3 y 60")
        @Digits(integer = 2, fraction = 1, message = "El porcentaje de grasa admite como mucho un decimal")
        BigDecimal porcentajeGrasa,

        @NotNull(message = "El objetivo es obligatorio")
        ObjetivoDeportivo objetivo,

        LocalDate fechaCompeticion,

        @NotBlank(message = "El deporte principal es obligatorio")
        @Size(max = 40, message = "El deporte principal no puede superar los 40 caracteres")
        String deportePrincipal,

        @NotNull(message = "El nivel de actividad es obligatorio")
        NivelActividad nivelActividad,

        @NotNull(message = "Los días de entrenamiento por semana son obligatorios")
        @Min(value = 0, message = "Los días de entrenamiento deben estar entre 0 y 7")
        @Max(value = 7, message = "Los días de entrenamiento deben estar entre 0 y 7")
        Integer diasEntrenamientoSemana,

        @NotNull(message = "La duración media es obligatoria")
        @Min(value = 0, message = "La duración media debe estar entre 0 y 600 minutos")
        @Max(value = 600, message = "La duración media debe estar entre 0 y 600 minutos")
        Integer duracionMediaMin,

        @NotNull(message = "El número de comidas al día es obligatorio")
        @Min(value = 3, message = "Las comidas al día deben estar entre 3 y 6")
        @Max(value = 6, message = "Las comidas al día deben estar entre 3 y 6")
        Integer comidasPorDia,

        @Min(value = 0, message = "El tiempo máximo de cocina debe estar entre 0 y 600 minutos")
        @Max(value = 600, message = "El tiempo máximo de cocina debe estar entre 0 y 600 minutos")
        Integer tiempoMaxCocinaMin) {
}
