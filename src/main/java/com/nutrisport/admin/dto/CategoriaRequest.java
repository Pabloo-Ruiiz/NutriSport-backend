package com.nutrisport.admin.dto;

import com.nutrisport.catalogo.entidad.TipoCategoria;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Crea o edita una categoría (§3.I). {@code seccionCompra} solo se admite en las de tipo ALIMENTO; el
 * padre, si se indica, debe ser del mismo tipo.
 */
public record CategoriaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String nombre,

        @NotNull(message = "El tipo es obligatorio")
        TipoCategoria tipo,

        UUID padreId,

        @Size(max = 40, message = "La sección no puede superar los 40 caracteres")
        String seccionCompra,

        @NotNull(message = "El orden es obligatorio")
        @Min(value = 0, message = "El orden debe estar entre 0 y 999")
        @Max(value = 999, message = "El orden debe estar entre 0 y 999")
        Integer orden) {
}
