package com.nutrisport.ia.entidad;

/** Función para la que se llama a la IA (CHECK ck_uso_ia_funcion de V8). */
public enum FuncionIa {
    /** Reconocimiento de alimentos en una foto. */
    ANALISIS_IMAGEN,
    /** Lectura de una etiqueta nutricional (Visión); comparte límite diario con ANALISIS_IMAGEN. */
    ETIQUETA,
    /** Mensajes al asistente (Ampliación). */
    ASISTENTE,
    /** Generación de texto interna (explicaciones, menús); sin límite diario por usuario. */
    TEXTO
}
