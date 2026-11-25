package com.nutrisport.recetas.entidad;

/**
 * Calidad del cálculo nutricional de una receta a partir de sus ingredientes (§4.9 y docs/API_INTEGRATIONS.md
 * §7). Los filtros por kcal o proteína solo se aplican a las recetas COMPLETO o APROXIMADO.
 */
public enum EstadoNutricional {

    /** Todos los ingredientes emparejados y con gramos exactos o estimados. */
    COMPLETO,

    /** La mayor parte de la masa resuelta; el resto, despreciable o estimado. */
    APROXIMADO,

    /** Faltan datos de ingredientes relevantes: no se muestran nutrientes por ración. */
    INCOMPLETO
}
