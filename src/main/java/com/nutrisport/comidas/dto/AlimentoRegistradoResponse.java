package com.nutrisport.comidas.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alimento de una comida con la instantánea de sus nutrientes en el momento del registro (§6.3):
 * no cambian aunque después se corrija el alimento del catálogo.
 */
public record AlimentoRegistradoResponse(
        UUID id,
        UUID alimentoId,
        String nombre,
        BigDecimal cantidad,
        String unidad,
        BigDecimal gramos,
        BigDecimal kcal,
        BigDecimal proteina,
        BigDecimal carbohidratos,
        BigDecimal grasa,
        boolean origenIa,
        BigDecimal confianzaIa) {
}
