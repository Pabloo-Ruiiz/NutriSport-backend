package com.nutrisport.perfil.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lista completa de preferencias que sustituye a la actual (PUT /api/v1/perfil/preferencias, §7.2). */
public record PreferenciasRequest(
        @NotNull(message = "La lista de preferencias es obligatoria (puede estar vacía)")
        @Size(max = 100, message = "No se pueden guardar más de 100 preferencias")
        List<@Valid @NotNull(message = "La preferencia no puede ser nula") PreferenciaDto> preferencias) {
}
