package com.nutrisport.menus.servicio;

import com.nutrisport.menus.entidad.TipoSugerencia;
import java.math.BigDecimal;

/**
 * Lo ÚNICO que puede devolver la IA al sugerir un ajuste (salida estructurada de prompts/sugerencia-menu.st). No lleva
 * nutrientes ni texto: el backend valida la propuesta y calcula las cifras.
 *
 * @param tipo                AJUSTE_CANTIDAD o SUSTITUCION
 * @param ingredienteOriginal nombre del ingrediente de la receta que cambia, tal como aparece en la lista
 * @param alimentoPropuesto   nombre del alimento (el mismo en un ajuste de cantidad)
 * @param gramos              gramos del alimento en la receta entera
 */
public record PropuestaAjusteIa(TipoSugerencia tipo, String ingredienteOriginal, String alimentoPropuesto,
        BigDecimal gramos) {
}
