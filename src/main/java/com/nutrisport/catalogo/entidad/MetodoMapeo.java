package com.nutrisport.catalogo.entidad;

/** Cómo se ha emparejado un ingrediente con un alimento (§6.1). */
public enum MetodoMapeo {

    /** Elegido por similitud de texto; pendiente de revisión si la confianza es baja. */
    AUTOMATICO,

    /** Fijado por un administrador. */
    MANUAL
}
