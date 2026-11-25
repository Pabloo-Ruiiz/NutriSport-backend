package com.nutrisport.compra.dto;

import java.time.LocalDate;

/**
 * Rango opcional para generar una lista desde un menú (POST /api/v1/listas-compra/desde-menu/{menuId}, §7.6). Sin
 * fechas, el menú completo. Que el rango esté dentro del menú lo comprueba el servicio.
 *
 * @param desde primer día (incluido); nulo = el primero del menú
 * @param hasta último día (incluido); nulo = el último del menú
 */
public record GenerarListaRequest(LocalDate desde, LocalDate hasta) {
}
