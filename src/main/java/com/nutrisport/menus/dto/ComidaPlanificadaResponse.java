package com.nutrisport.menus.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comida de un menú (§7.5).
 *
 * @param nutrientes los de una ración de la receta por {@code raciones}; nulos si la receta no tiene cálculo
 *                   COMPLETO o APROXIMADO
 */
public record ComidaPlanificadaResponse(UUID id, LocalDate fecha, TipoComida tipoComida, RecetaResumenResponse receta,
        BigDecimal raciones, Nutrientes nutrientes, boolean bloqueada, boolean consumida) {
}
