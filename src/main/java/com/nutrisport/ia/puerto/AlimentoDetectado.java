package com.nutrisport.ia.puerto;

import com.nutrisport.catalogo.entidad.Preparacion;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alimento que la IA ve en una foto (§4.3). Solo identifica y estima la cantidad: los nutrientes se
 * calculan después con el catálogo (§4.1).
 *
 * @param nombre          nombre común en español, p. ej. "Arroz blanco"
 * @param preparacion     forma de preparación visible
 * @param gramosEstimados porción estimada en gramos (entre 0 y 2000, sin incluir el 0)
 * @param confianza       seguridad del modelo entre 0 y 1
 * @param alternativas    otros nombres posibles si la identificación es dudosa
 */
public record AlimentoDetectado(String nombre, Preparacion preparacion, BigDecimal gramosEstimados,
        BigDecimal confianza, List<String> alternativas) {

    public AlimentoDetectado {
        alternativas = alternativas == null ? List.of() : List.copyOf(alternativas);
    }
}
