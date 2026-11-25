package com.nutrisport.menus.dto;

import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.TipoMenu;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detalle de un menú con sus días (todos los del rango, también los que no tienen comidas) y su resumen (§7.5). */
public record MenuResponse(
        UUID id,
        String nombre,
        TipoMenu tipo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean favorito,
        EstadoMenu estado,
        boolean generadoAutomaticamente,
        String explicacion,
        List<DiaMenuResponse> dias,
        Resumen resumen) {

    public MenuResponse {
        dias = List.copyOf(dias);
    }

    /**
     * Resumen del menú.
     *
     * @param mediaKcal        kcal planificadas por día, de media
     * @param ajustePorcentaje kcal planificadas de todo el menú sobre las de sus objetivos (100 = ajuste exacto); nulo
     *                         sin objetivos
     * @param proteinaMedia    gramos de proteína planificados por día, de media
     */
    public record Resumen(BigDecimal mediaKcal, Integer ajustePorcentaje, BigDecimal proteinaMedia) {
    }
}
