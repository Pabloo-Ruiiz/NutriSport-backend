package com.nutrisport.seguimiento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Evolución del peso entre dos fechas (GET /api/v1/seguimiento/peso, §3.F y §7.7).
 *
 * @param puntos    un punto por día con pesaje, en orden cronológico
 * @param variacion peso del último punto menos el del primero; nula si hay menos de dos puntos
 */
public record SeriePesoResponse(List<PuntoPeso> puntos, BigDecimal variacion) {

    public SeriePesoResponse {
        puntos = List.copyOf(puntos);
    }

    /**
     * @param fecha       día del pesaje
     * @param pesoKg      peso de ese día
     * @param mediaMovil7 media de los pesajes de los 7 días naturales que terminan en {@code fecha}
     *                    (incluidos los anteriores al inicio de la serie), con dos decimales
     */
    public record PuntoPeso(LocalDate fecha, BigDecimal pesoKg, BigDecimal mediaMovil7) {
    }
}
