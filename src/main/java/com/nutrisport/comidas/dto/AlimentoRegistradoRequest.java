package com.nutrisport.comidas.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un alimento de la comida: el alimento del catálogo ({@code alimentoId}) o un producto por su código de
 * barras ({@code codigoBarras}, Ampliación), exactamente uno de los dos. La cantidad va en {@code unidad}, que
 * puede ser g, kg, ml, l (solo líquidos) o el nombre de una porción casera del alimento ("unidad mediana").
 */
public record AlimentoRegistradoRequest(
        UUID alimentoId,

        @Pattern(regexp = "\\d{8,14}", message = "El código de barras debe tener entre 8 y 14 dígitos")
        String codigoBarras,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor que 0")
        @DecimalMax(value = "5000", message = "La cantidad no puede superar 5000")
        @Digits(integer = 4, fraction = 2, message = "La cantidad admite como mucho dos decimales")
        BigDecimal cantidad,

        @NotBlank(message = "La unidad es obligatoria")
        @Size(max = 40, message = "La unidad no puede superar los 40 caracteres")
        String unidad) {

    /** Alimento del catálogo con su cantidad (registro manual o confirmación de una foto). */
    public AlimentoRegistradoRequest(UUID alimentoId, BigDecimal cantidad, String unidad) {
        this(alimentoId, null, cantidad, unidad);
    }

    /** Si viene por código de barras. */
    public boolean porCodigo() {
        return codigoBarras != null;
    }

    /** Validación: exactamente uno de los dos (el error se informa en el campo {@code referenciaValida}). */
    @JsonIgnore
    @AssertTrue(message = "Indica alimentoId o codigoBarras, pero no los dos")
    public boolean isReferenciaValida() {
        return (alimentoId == null) != (codigoBarras == null);
    }
}
