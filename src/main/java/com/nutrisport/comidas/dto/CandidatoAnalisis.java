package com.nutrisport.comidas.dto;

import com.nutrisport.catalogo.entidad.Preparacion;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alimento detectado en una foto con sus mejores coincidencias del catálogo (§4.3), para que el usuario
 * lo revise antes de confirmar.
 *
 * @param nombreDetectado nombre que dio la IA
 * @param preparacion     preparación detectada
 * @param gramosEstimados porción estimada por la IA (editable)
 * @param confianza       confianza de la IA, de 0 a 1
 * @param nivelConfianza  ALTA (≥ 0,80), MEDIA (≥ 0,50) o BAJA
 * @param estimado        {@code true} si no lo detectó la IA sino una regla (p. ej. el aceite de un frito)
 * @param coincidencias   hasta 3, primero las de la misma preparación; la primera es la propuesta
 */
public record CandidatoAnalisis(String nombreDetectado, Preparacion preparacion, BigDecimal gramosEstimados,
        BigDecimal confianza, String nivelConfianza, boolean estimado, List<CoincidenciaCatalogo> coincidencias) {

    public CandidatoAnalisis {
        coincidencias = coincidencias == null ? List.of() : List.copyOf(coincidencias);
    }
}
