package com.nutrisport.perfil.dto;

import com.nutrisport.perfil.entidad.Alergeno;
import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.entidad.Intolerancia;
import com.nutrisport.perfil.entidad.Severidad;
import com.nutrisport.perfil.entidad.TipoPreferencia;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Una preferencia o restricción alimentaria, en la petición y en la respuesta. Según el tipo se
 * rellena un solo dato: {@code ALERGIA} → alergeno, {@code INTOLERANCIA} → intolerancia,
 * {@code DIETA} → dieta y {@code RECHAZO}/{@code FAVORITO} → alimentoId o categoriaId. La coherencia
 * la comprueba el servicio.
 */
public record PreferenciaDto(
        @NotNull(message = "El tipo es obligatorio")
        TipoPreferencia tipo,

        Alergeno alergeno,

        Intolerancia intolerancia,

        Dieta dieta,

        UUID alimentoId,

        UUID categoriaId,

        @NotNull(message = "La severidad es obligatoria")
        Severidad severidad) {
}
