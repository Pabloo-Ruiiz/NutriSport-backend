package com.nutrisport.recetas.entidad;

/** Cómo se ha convertido a gramos la medida original de un ingrediente ("200g", "1 tbsp", "pinch"...). */
public enum EstadoConversion {

    /** La medida ya venía en gramos o mililitros. */
    EXACTA,

    /** Convertida con una equivalencia (cucharadas, tazas, unidades...). */
    ESTIMADA,

    /** Cantidad tan pequeña que no cuenta para el cálculo ("pinch", "to taste"). */
    DESPRECIABLE,

    /** No se ha podido convertir. */
    SIN_DATOS
}
