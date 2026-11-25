package com.nutrisport.recetas.dto;

import com.nutrisport.catalogo.dto.FuenteResponse;
import com.nutrisport.recetas.entidad.Dificultad;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.PasoReceta;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una receta (§7.11). Las cantidades de los ingredientes son para {@code raciones}; los nutrientes,
 * siempre por ración.
 *
 * @param categoria           nombre de la categoría propia; nulo si no tiene
 * @param raciones            raciones para las que están calculadas las cantidades (las pedidas o las de la receta)
 * @param racionesSupuestas   {@code true} si las raciones de la receta son supuestas (TheMealDB no las informa)
 * @param instrucciones       pasos en orden (en inglés si {@code idioma = en})
 * @param nutricionRacion     nula mientras el cálculo nutricional esté INCOMPLETO
 * @param alergenos           alérgenos que contiene (constantes de Alergeno), derivados de los ingredientes
 * @param alergenosVerificados {@code true} solo si todos los ingredientes tienen alérgenos revisados
 * @param compatible          si encaja con las restricciones estrictas del usuario
 * @param fuente              procedencia y atribución; nula en las recetas propias
 */
public record RecetaResponse(
        UUID id,
        OrigenReceta origen,
        String nombre,
        String descripcion,
        String idioma,
        String categoria,
        String area,
        String imagenUrl,
        int raciones,
        boolean racionesSupuestas,
        Short tiempoPreparacionMin,
        Dificultad dificultad,
        List<String> franjasAptas,
        List<String> etiquetas,
        List<IngredienteRecetaResponse> ingredientes,
        List<PasoReceta> instrucciones,
        NutricionRacion nutricionRacion,
        EstadoNutricional estadoNutricional,
        int completitudPct,
        List<String> alergenos,
        boolean alergenosVerificados,
        boolean compatible,
        boolean favorita,
        FuenteResponse fuente) {

    public RecetaResponse {
        franjasAptas = List.copyOf(franjasAptas);
        etiquetas = List.copyOf(etiquetas);
        ingredientes = List.copyOf(ingredientes);
        instrucciones = List.copyOf(instrucciones);
        alergenos = List.copyOf(alergenos);
    }
}
