package com.nutrisport.perfil.entidad;

/** Tipo de preferencia alimentaria (CHECK ck_preferencia_alimentaria_tipo de V3). */
public enum TipoPreferencia {
    /** Exige alérgeno. */
    ALERGIA,
    /** Exige intolerancia. */
    INTOLERANCIA,
    /** Exige dieta. */
    DIETA,
    /** Exige alimento o categoría. */
    RECHAZO,
    /** Exige alimento o categoría. */
    FAVORITO
}
