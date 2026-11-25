package com.nutrisport.seguimiento.entidad;

/** Cómo se presenta un insight (CHECK ck_insight_severidad de V17). */
public enum SeveridadInsight {

    /** Dato curioso o hábito a vigilar. */
    INFO,

    /** Algo que el usuario hace bien. */
    POSITIVO,

    /** Algo que conviene corregir. */
    ADVERTENCIA
}
