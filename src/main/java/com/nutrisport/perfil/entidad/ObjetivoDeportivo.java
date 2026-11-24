package com.nutrisport.perfil.entidad;

/** Objetivo deportivo del perfil (CHECK ck_perfil_deportivo_objetivo de V3). */
public enum ObjetivoDeportivo {
    PERDER_GRASA,
    MANTENER,
    GANAR_MUSCULO,
    RENDIMIENTO,
    /** Exige fecha de competición. */
    COMPETICION
}
