package com.nutrisport.menus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Reutilizar un menú en otras fechas (POST /api/v1/menus/{id}/duplicar, §3.D y §7.5). Que fechaInicio no sea anterior
 * a hoy lo comprueba el servicio.
 *
 * @param fechaInicio primer día de la copia; el resto de días se desplazan lo mismo
 * @param nombre      opcional; si no se indica, "Copia de …"
 */
public record DuplicarMenuRequest(
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre) {
}
