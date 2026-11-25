package com.nutrisport.menus.dto;

import com.nutrisport.comidas.entidad.TipoComida;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cambios de una comida planificada (PUT /api/v1/menus/{id}/comidas/{comidaId}, §7.5): cambiar la receta, las
 * raciones, moverla de día o de franja, o bloquearla. Los campos nulos no cambian. Que la fecha esté dentro del menú
 * y que la receta se pueda usar lo comprueba el servicio.
 */
public record ActualizarComidaPlanificadaRequest(
        UUID recetaId,

        @Positive(message = "Las raciones deben ser mayores que 0")
        @DecimalMax(value = "10", message = "Las raciones no pueden superar 10")
        @Digits(integer = 2, fraction = 2, message = "Las raciones admiten como mucho dos decimales")
        BigDecimal raciones,

        LocalDate fecha,

        TipoComida tipoComida,

        Boolean bloqueada) {
}
