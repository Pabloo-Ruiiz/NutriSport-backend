package com.nutrisport.compra.entidad;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cantidad de un producto que necesita un día del menú (elemento de la columna jsonb {@code desglose}):
 * {@code {"fecha": "2026-11-30", "gramos": 200.0}}.
 *
 * @param gramos gramos, o mililitros si la unidad base del alimento es ML, con un decimal
 */
public record EntradaDesglose(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate fecha,

        BigDecimal gramos) {
}
