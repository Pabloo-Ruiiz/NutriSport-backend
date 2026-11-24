package com.nutrisport.perfil.entidad;

/**
 * Dieta del usuario (CHECK ck_preferencia_alimentaria_dieta de V3). No hay dietas "sin gluten" ni
 * "sin lactosa": son alergias o intolerancias.
 */
public enum Dieta {
    VEGETARIANA,
    VEGANA,
    PESCETARIANA,
    /** Solo como preferencia blanda en el MVP. */
    HALAL,
    /** Solo como preferencia blanda en el MVP. */
    KOSHER
}
