package com.nutrisport.catalogo.entidad;

/** Procedencia de los valores nutricionales de un alimento, para su trazabilidad (CHECK ck_alimento_fuente de V4). */
public enum FuenteDato {
    /** Base de Datos Española de Composición de Alimentos. */
    BEDCA,
    /** FoodData Central del Departamento de Agricultura de EE. UU. */
    USDA,
    OPEN_FOOD_FACTS,
    /** Creado o corregido por un administrador. */
    ADMIN,
    /** Alimento privado creado por un usuario. */
    USUARIO,
    /** Leído de una etiqueta nutricional (Visión). */
    OCR
}
