package com.nutrisport.menus.dto;

import jakarta.validation.constraints.Size;

/**
 * Petición de una sugerencia de la IA para una comida planificada (§4.5).
 *
 * @param preferencia lo que quiere el usuario en lenguaje natural ("cambia la nata por yogur sin lactosa"); opcional
 */
public record SugerenciaIaRequest(
        @Size(max = 300, message = "La preferencia no puede superar los 300 caracteres")
        String preferencia) {
}
