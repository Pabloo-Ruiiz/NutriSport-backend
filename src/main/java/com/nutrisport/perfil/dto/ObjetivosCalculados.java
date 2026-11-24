package com.nutrisport.perfil.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del cálculo de los objetivos base (sin entrenamiento) con su desglose (§3.B).
 *
 * @param edad             años cumplidos en la fecha de referencia
 * @param metabolismoBasal kcal/día según Mifflin-St Jeor (2 decimales)
 * @param factorActividad  factor por la actividad fuera del entrenamiento
 * @param gastoSinEntreno  metabolismo basal × factor de actividad, en kcal/día (2 decimales)
 * @param ajustePorcentaje ajuste por objetivo en % (−15, 0 o +10), antes de los límites de seguridad
 * @param kcal             energía objetivo, entera
 * @param proteinaG        proteína en gramos, entera
 * @param carbohidratosG   carbohidratos en gramos, entero
 * @param grasaG           grasa en gramos, entera
 * @param avisos           límites de seguridad aplicados; vacía si no se aplicó ninguno
 */
public record ObjetivosCalculados(
        int edad,
        BigDecimal metabolismoBasal,
        BigDecimal factorActividad,
        BigDecimal gastoSinEntreno,
        int ajustePorcentaje,
        BigDecimal kcal,
        BigDecimal proteinaG,
        BigDecimal carbohidratosG,
        BigDecimal grasaG,
        List<String> avisos) {

    public ObjetivosCalculados {
        avisos = List.copyOf(avisos);
    }
}
