package com.nutrisport.comidas.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Registra o sustituye una comida (POST y PUT /api/v1/comidas, §7.4 y §7.11). La fecha puede ser como
 * mucho mañana (lo comprueba el servicio con el reloj de la aplicación).
 */
public record RegistroComidaRequest(
        @NotNull(message = "La fecha es obligatoria")
        LocalDate fecha,

        LocalTime hora,

        @NotNull(message = "El tipo de comida es obligatorio")
        TipoComida tipoComida,

        @Size(max = 500, message = "Las notas no pueden superar los 500 caracteres")
        String notas,

        @NotNull(message = "Los alimentos son obligatorios")
        @Size(min = 1, max = 40, message = "La comida debe tener entre 1 y 40 alimentos")
        List<@Valid @NotNull(message = "El alimento no puede ser nulo") AlimentoRegistradoRequest> alimentos) {
}
