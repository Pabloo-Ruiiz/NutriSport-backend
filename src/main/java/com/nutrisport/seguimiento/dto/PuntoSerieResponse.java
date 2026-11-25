package com.nutrisport.seguimiento.dto;

import com.nutrisport.comun.dto.Nutrientes;
import java.time.LocalDate;

/**
 * Un punto de la serie de estadísticas: un día, una semana o un mes del rango.
 *
 * @param periodo         "2026-11-24" (DIA), "2026-W48" (SEMANA ISO) o "2026-11" (MES)
 * @param desde           primer día del periodo dentro del rango pedido
 * @param consumido       media diaria de los días con algún registro (en DIA, lo del día); cero si no hay ninguno
 * @param objetivo        media de los objetivos guardados de los días con registro (si no hay, de los días que tengan
 *                        objetivo); nulo si ningún día del periodo tiene objetivo
 * @param diasConRegistro días del periodo con al menos una comida
 * @param diasEnObjetivo  días con registro cuya energía está en ±10 % de su objetivo
 */
public record PuntoSerieResponse(String periodo, LocalDate desde, Nutrientes consumido, Nutrientes objetivo,
        int diasConRegistro, int diasEnObjetivo) {
}
