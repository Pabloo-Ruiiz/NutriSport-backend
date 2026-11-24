package com.nutrisport.comidas.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Datos que el usuario confirma para registrar la comida propuesta por el análisis. */
public record ConfirmarAnalisisRequest(
        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        LocalTime hora,

        @NotNull(message = "El tipo de comida es obligatorio")
        TipoComida tipoComida,

        @NotNull(message = "Los alimentos son obligatorios")
        @Size(min = 1, max = 20, message = "La comida debe tener entre 1 y 20 alimentos")
        List<@Valid @NotNull(message = "El alimento no puede ser nulo") AlimentoConfirmadoRequest> alimentos) {
}
