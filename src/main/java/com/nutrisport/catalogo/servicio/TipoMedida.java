package com.nutrisport.catalogo.servicio;

/** Clase de una medida de ingrediente interpretada por {@link AnalizadorMedidas}. */
public enum TipoMedida {

    /** g, kg, oz, lb: se convierte a gramos directamente. */
    MASA,

    /** ml, l, cup, tbsp, tsp: necesita las medidas caseras del alimento o una densidad. */
    VOLUMEN,

    /** Un número solo ("10"), "1 chopped", "1 clove", "handful"...: necesita la porción por unidad del alimento. */
    UNIDAD,

    /** pinch, to taste, garnish: cuenta como 0 g. */
    DESPRECIABLE,

    /** No se ha podido interpretar. */
    DESCONOCIDA
}
