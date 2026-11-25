package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.UnidadCompra;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cambios de un producto (PATCH /api/v1/listas-compra/{id}/productos/{pid}, §7.6). Los campos nulos no cambian. */
public record ActualizarProductoRequest(
        @Positive(message = "La cantidad debe ser mayor que 0")
        @DecimalMax(value = "99999.99", message = "La cantidad no puede superar 99999,99")
        @Digits(integer = 5, fraction = 2, message = "La cantidad admite como mucho dos decimales")
        BigDecimal cantidad,

        UnidadCompra unidad,

        Boolean comprado) {
}
