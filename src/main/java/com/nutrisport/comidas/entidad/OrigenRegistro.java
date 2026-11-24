package com.nutrisport.comidas.entidad;

/** Cómo se registró una comida (CHECK ck_registro_comida_origen de V5). */
public enum OrigenRegistro {
    /** Buscando y añadiendo alimentos a mano. */
    MANUAL,
    /** A partir del análisis de una foto con IA, confirmado por el usuario. */
    FOTO,
    /** Desde el menú semanal planificado (Ampliación). */
    PLAN,
    /** Desde una receta del catálogo (Ampliación). */
    RECETA,
    /** Duplicando otra comida. */
    COPIA,
    /** Escaneando el código de barras de un producto (Ampliación). */
    CODIGO_BARRAS
}
