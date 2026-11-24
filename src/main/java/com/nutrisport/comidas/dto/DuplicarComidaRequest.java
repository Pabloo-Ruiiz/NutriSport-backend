package com.nutrisport.comidas.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Copia una comida a otra fecha ("repetir el desayuno de ayer"); si no se indica tipo, se mantiene. */
public record DuplicarComidaRequest(
        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        TipoComida tipoComida) {
}
