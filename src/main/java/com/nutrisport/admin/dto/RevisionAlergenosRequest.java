package com.nutrisport.admin.dto;

import com.nutrisport.perfil.entidad.Alergeno;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Revisión de los alérgenos de un alimento por un administrador (§3.I y §6.3): sustituye los alérgenos
 * que contiene y sus trazas, y lo marca como revisado. Conjuntos vacíos = sin alérgenos.
 */
public record RevisionAlergenosRequest(
        @NotNull(message = "Indica los alérgenos que contiene (puede estar vacío)")
        Set<@NotNull Alergeno> alergenosContiene,

        @NotNull(message = "Indica las trazas (puede estar vacío)")
        Set<@NotNull Alergeno> alergenosTrazas,

        @NotNull(message = "Indica si contiene lactosa")
        Boolean contieneLactosa) {
}
