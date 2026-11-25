package com.nutrisport.menus.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Aplicar una sugerencia válida de la IA a su comida planificada (§4.5). */
public record AplicarSugerenciaRequest(
        @NotNull(message = "La sugerencia es obligatoria")
        UUID sugerenciaId) {
}
