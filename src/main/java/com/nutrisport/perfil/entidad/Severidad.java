package com.nutrisport.perfil.entidad;

/** Severidad de una preferencia (CHECK ck_preferencia_alimentaria_severidad de V3). */
public enum Severidad {
    /** Filtro duro: se excluye siempre. */
    ESTRICTA,
    /** Preferencia blanda: solo influye en la puntuación. */
    PREFERENCIA
}
