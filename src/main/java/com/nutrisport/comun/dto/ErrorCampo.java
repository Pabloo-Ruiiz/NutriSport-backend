package com.nutrisport.comun.dto;

/**
 * Error de validación de un campo, dentro de la propiedad {@code errors} de un Problem Details.
 *
 * @param campo   nombre del campo o parámetro (en camelCase, como en el JSON)
 * @param mensaje motivo del error, en castellano
 */
public record ErrorCampo(String campo, String mensaje) {
}
