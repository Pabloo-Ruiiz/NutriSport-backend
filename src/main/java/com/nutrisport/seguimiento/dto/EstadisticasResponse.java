package com.nutrisport.seguimiento.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.seguimiento.entidad.Agrupacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Estadísticas de un rango de días (GET /api/v1/seguimiento/estadisticas, §3.F y §7.7). Las medias se calculan sobre
 * los días con al menos una comida registrada.
 *
 * @param serie               un punto por día, semana o mes según {@code agrupacion}
 * @param mediaDiaria         media de lo consumido en los días con registro; cero si no hay ninguno
 * @param objetivoMedio       media de los objetivos de esos días; nulo si no hay ninguno con objetivo
 * @param diasConRegistro     días del rango con al menos una comida
 * @param diasEnObjetivo      días con registro cuya energía está en ±10 % del objetivo de ese día
 * @param porcentajeAdherencia diasEnObjetivo / diasConRegistro × 100, entero (0 sin registros)
 * @param repartoMacrosPct    porcentaje de la energía de la media diaria que aporta cada macro ("proteina",
 *                            "carbohidratos", "grasa"; 4, 4 y 9 kcal/g); suman 100 (o todo 0 sin datos)
 * @param kcalPorTipoComida   kcal medias al día de cada tipo de comida registrado (suman la energía media)
 * @param top                 hasta 10 alimentos más consumidos, por número de veces
 */
public record EstadisticasResponse(LocalDate desde, LocalDate hasta, Agrupacion agrupacion,
        List<PuntoSerieResponse> serie, Nutrientes mediaDiaria, Nutrientes objetivoMedio, int diasConRegistro,
        int diasEnObjetivo, int porcentajeAdherencia, Map<String, Integer> repartoMacrosPct,
        Map<TipoComida, BigDecimal> kcalPorTipoComida, List<AlimentoFrecuenteResponse> top) {
}
