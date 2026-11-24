package com.nutrisport.perfil.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Objetivos base vigentes (sin entrenamiento) y desglose del cálculo automático (GET/PUT
 * /api/v1/perfil/objetivos, §7.2).
 *
 * @param kcal           energía objetivo guardada en el perfil
 * @param proteinaG      proteína objetivo en gramos
 * @param carbohidratosG carbohidratos objetivo en gramos
 * @param grasaG         grasa objetivo en gramos
 * @param manuales       {@code true} si los fijó el usuario y no se recalculan
 * @param desglose       cálculo automático con los datos actuales del perfil (también si son manuales,
 *                       para poder compararlos)
 * @param avisos         avisos de seguridad de los objetivos vigentes; no impiden guardarlos
 */
public record ObjetivosResponse(
        BigDecimal kcal,
        BigDecimal proteinaG,
        BigDecimal carbohidratosG,
        BigDecimal grasaG,
        boolean manuales,
        ObjetivosCalculados desglose,
        List<String> avisos) {

    public ObjetivosResponse {
        avisos = List.copyOf(avisos);
    }
}
