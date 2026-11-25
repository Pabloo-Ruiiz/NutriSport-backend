package com.nutrisport.menus.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comida planificada aún no consumida de un menú ACTIVO (resumen diario, §3.F). Con {@code menuId} e {@code id} se
 * registra con POST /api/v1/menus/{menuId}/comidas/{id}/registrar.
 *
 * @param nutrientes los de una ración × raciones; nulos si la receta no tiene cálculo COMPLETO o APROXIMADO
 */
public record ComidaPendienteResponse(UUID menuId, String menuNombre, UUID id, LocalDate fecha, TipoComida tipoComida,
        RecetaResumenResponse receta, BigDecimal raciones, Nutrientes nutrientes) {
}
