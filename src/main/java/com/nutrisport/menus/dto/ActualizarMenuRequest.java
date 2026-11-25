package com.nutrisport.menus.dto;

import com.nutrisport.menus.entidad.EstadoMenu;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cambios de un menú (§7.5): renombrar, marcar como favorito o cambiar de estado. Los campos nulos no cambian. */
public record ActualizarMenuRequest(
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        Boolean favorito,

        EstadoMenu estado) {
}
