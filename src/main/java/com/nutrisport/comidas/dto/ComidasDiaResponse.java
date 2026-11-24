package com.nutrisport.comidas.dto;

import com.nutrisport.comun.dto.Nutrientes;
import java.time.LocalDate;
import java.util.List;

/** Comidas de un día en orden cronológico y la suma de sus totales. */
public record ComidasDiaResponse(LocalDate fecha, List<RegistroComidaResponse> comidas, Nutrientes totales) {
}
