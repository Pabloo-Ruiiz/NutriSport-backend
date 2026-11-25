package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.EstadoLista;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una lista de la compra (§7.6), agrupado por sección.
 *
 * @param menuId    menú del que se generó; nulo si no sale de un menú o si se borró
 * @param comprados productos marcados como comprados
 * @param total     productos de la lista
 * @param secciones en el orden de las categorías de alimentos (las secciones que no son de ninguna categoría, al
 *                  final y por orden alfabético); dentro de cada una, los comprados al final
 */
public record ListaCompraResponse(UUID id, String nombre, EstadoLista estado, UUID menuId, LocalDate fechaDesde,
        LocalDate fechaHasta, int comprados, int total, List<SeccionCompraResponse> secciones) {
}
