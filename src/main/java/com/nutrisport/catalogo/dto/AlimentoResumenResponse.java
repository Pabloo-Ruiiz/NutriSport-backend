package com.nutrisport.catalogo.dto;

import com.nutrisport.catalogo.entidad.FuenteDato;
import java.math.BigDecimal;
import java.util.UUID;

/** Alimento en los resultados de búsqueda (GET /api/v1/alimentos, §7.3): valores por 100 g o 100 ml. */
public record AlimentoResumenResponse(
        UUID id,
        String nombre,
        String marca,
        BigDecimal kcal100,
        BigDecimal proteinas100,
        BigDecimal carbohidratos100,
        BigDecimal grasas100,
        FuenteDato fuente) {
}
