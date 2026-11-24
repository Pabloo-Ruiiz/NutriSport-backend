package com.nutrisport.actividad.entidad;

/** Estado de un entrenamiento (CHECK ck_entrenamiento_estado de V6). */
public enum EstadoEntrenamiento {
    /** Previsto; cuenta para el objetivo diario. */
    PLANIFICADO,
    /** Hecho; cuenta para el objetivo diario. */
    REALIZADO,
    /** No se hizo; no cuenta. Lo marca la tarea nocturna si un planificado de ayer no se realizó. */
    OMITIDO
}
