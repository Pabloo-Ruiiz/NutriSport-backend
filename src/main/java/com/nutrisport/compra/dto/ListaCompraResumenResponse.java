package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.EstadoLista;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Lista de la compra en el listado (§7.6), con su progreso. */
public record ListaCompraResumenResponse(UUID id, String nombre, EstadoLista estado, UUID menuId, LocalDate fechaDesde,
        LocalDate fechaHasta, int comprados, int total, OffsetDateTime creadoEn) {
}
