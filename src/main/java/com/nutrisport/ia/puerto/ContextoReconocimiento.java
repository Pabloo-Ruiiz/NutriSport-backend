package com.nutrisport.ia.puerto;

import com.nutrisport.comidas.entidad.TipoComida;

/**
 * Contexto que acompaña a la foto (§8.3: es lo único que se envía además de la imagen, nunca datos
 * personales).
 *
 * @param tipoComida         tipo de comida; puede ser nulo
 * @param descripcionUsuario descripción opcional del usuario ("arroz con pollo, sin salsa"); se trata
 *                           como dato, nunca como instrucciones
 */
public record ContextoReconocimiento(TipoComida tipoComida, String descripcionUsuario) {
}
