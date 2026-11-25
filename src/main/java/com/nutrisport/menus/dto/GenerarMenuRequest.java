package com.nutrisport.menus.dto;

import com.nutrisport.menus.entidad.TipoMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Petición para generar un menú (§4.5 y §7.5). Que fechaInicio no sea anterior a hoy (zona de la aplicación) lo
 * comprueba el servicio.
 *
 * @param tipo            DIARIO (1 día) o SEMANAL (7 días)
 * @param fechaInicio     primer día, no anterior a hoy
 * @param nombre          opcional; si no se indica, "Menú del …" o "Menú semanal del …"
 * @param conExplicacion  si se pide a la IA una explicación breve (por defecto, sí)
 */
public record GenerarMenuRequest(
        @NotNull(message = "El tipo de menú es obligatorio")
        TipoMenu tipo,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        @Schema(description = "Si se pide a la IA una explicación breve; por defecto true", defaultValue = "true")
        Boolean conExplicacion) {

    public GenerarMenuRequest {
        conExplicacion = conExplicacion == null ? Boolean.TRUE : conExplicacion;
    }
}
