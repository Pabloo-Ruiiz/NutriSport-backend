package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.EstadoLista;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Renombrar o completar una lista (PATCH /api/v1/listas-compra/{id}, §7.6). Los campos nulos no cambian. */
public record ActualizarListaRequest(
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        @Pattern(regexp = "(?s).*\\S.*", message = "El nombre no puede estar vacío")
        String nombre,

        EstadoLista estado) {
}
