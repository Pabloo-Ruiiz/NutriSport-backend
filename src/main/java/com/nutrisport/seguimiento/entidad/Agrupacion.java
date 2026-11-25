package com.nutrisport.seguimiento.entidad;

/** Cómo se agrupan los días en la serie de las estadísticas (§7.7) o qué periodo se compara. */
public enum Agrupacion {

    /** Un punto por día. */
    DIA,

    /** Un punto por semana ISO (de lunes a domingo). */
    SEMANA,

    /** Un punto por mes natural. */
    MES
}
