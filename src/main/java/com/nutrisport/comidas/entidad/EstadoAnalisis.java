package com.nutrisport.comidas.entidad;

/** Estado de un análisis de foto (CHECK ck_analisis_imagen_estado de V9). */
public enum EstadoAnalisis {
    /** Foto guardada, a la espera del procesador asíncrono. */
    PENDIENTE,
    /** Enviada a la IA. */
    PROCESANDO,
    /** Con candidatos listos para que el usuario los revise. */
    COMPLETADO,
    /** La IA no respondió o no detectó alimentos; ver errorMensaje. */
    FALLIDO,
    /** El usuario confirmó y se creó el registro de comida (o el alimento, en etiquetas). */
    CONFIRMADO,
    /** El usuario lo descartó. */
    DESCARTADO
}
