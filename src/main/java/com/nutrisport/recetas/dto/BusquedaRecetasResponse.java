package com.nutrisport.recetas.dto;

import com.nutrisport.comun.dto.PaginaResponse;
import java.util.List;

/**
 * Resultado de {@code GET /recetas} (§7.3): la página de recetas del catálogo y, aparte, las de TheMealDB que
 * aún no están importadas (solo en la primera página, sin paginar: TheMealDB no pagina). Van separadas para
 * que la paginación del catálogo siga siendo correcta.
 *
 * @param catalogo                  recetas del catálogo (públicas publicadas y propias)
 * @param themealdb                 recetas de TheMealDB no importadas (estadoNutricional PENDIENTE)
 * @param themealdbDisponible       {@code false} si había que consultar TheMealDB y no estaba disponible
 */
public record BusquedaRecetasResponse(
        PaginaResponse<RecetaResumenResponse> catalogo,
        List<RecetaResumenResponse> themealdb,
        boolean themealdbDisponible) {

    public BusquedaRecetasResponse {
        themealdb = List.copyOf(themealdb);
    }
}
