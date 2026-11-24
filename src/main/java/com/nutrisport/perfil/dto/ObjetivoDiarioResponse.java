package com.nutrisport.perfil.dto;

import com.nutrisport.comun.dto.Nutrientes;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Objetivo nutricional de un día (§3.B y §3.G).
 *
 * @param fecha       día
 * @param base        objetivos base del perfil ese día (sin entrenamiento)
 * @param kcalEntreno gasto neto de los entrenamientos planificados y realizados del día
 * @param total       base + entrenamiento; el extra va entero a carbohidratos (kcal / 4)
 */
public record ObjetivoDiarioResponse(LocalDate fecha, Nutrientes base, BigDecimal kcalEntreno, Nutrientes total) {
}
