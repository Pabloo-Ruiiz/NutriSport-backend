package com.nutrisport.admin.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Publicación de una receta pública (§7.10). Los campos nulos no cambian.
 *
 * @param publicada    si entra en el planificador
 * @param raciones     raciones reales: dejan de ser supuestas y se recalculan los nutrientes por ración
 * @param franjasAptas comidas del día en las que encaja la receta
 */
public record PublicarRecetaRequest(
        Boolean publicada,

        @Min(value = 1, message = "Las raciones deben estar entre 1 y 20")
        @Max(value = 20, message = "Las raciones deben estar entre 1 y 20")
        Integer raciones,

        @Size(max = 7, message = "Como mucho 7 franjas")
        Set<TipoComida> franjasAptas) {
}
