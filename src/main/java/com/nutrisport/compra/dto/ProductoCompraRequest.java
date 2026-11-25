package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.UnidadCompra;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Producto libre añadido a mano a una lista (POST /api/v1/listas-compra/{id}/productos, §7.6).
 *
 * @param seccion opcional; por defecto "Otros"
 */
public record ProductoCompraRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
        String nombre,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que 0")
        @DecimalMax(value = "99999.99", message = "La cantidad no puede superar 99999,99")
        @Digits(integer = 5, fraction = 2, message = "La cantidad admite como mucho dos decimales")
        BigDecimal cantidad,

        @NotNull(message = "La unidad es obligatoria")
        UnidadCompra unidad,

        @Size(max = 40, message = "La sección no puede superar los 40 caracteres")
        String seccion) {
}
