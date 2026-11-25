package com.nutrisport.menus.dto;

import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.TipoMenu;
import java.time.LocalDate;
import java.util.UUID;

/** Menú en el listado (§7.5). */
public record MenuResumenResponse(UUID id, String nombre, TipoMenu tipo, LocalDate fechaInicio, LocalDate fechaFin,
        boolean favorito, EstadoMenu estado, boolean generadoAutomaticamente, int numeroComidas) {
}
