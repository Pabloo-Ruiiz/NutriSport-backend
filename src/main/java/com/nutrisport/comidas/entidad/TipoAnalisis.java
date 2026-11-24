package com.nutrisport.comidas.entidad;

/** Qué se analiza en la foto (CHECK ck_analisis_imagen_tipo de V9). */
public enum TipoAnalisis {
    /** Una comida: se identifican los alimentos y se estiman las cantidades (MVP). */
    COMIDA,
    /** Una etiqueta nutricional: se transcriben sus valores (Visión). */
    ETIQUETA
}
