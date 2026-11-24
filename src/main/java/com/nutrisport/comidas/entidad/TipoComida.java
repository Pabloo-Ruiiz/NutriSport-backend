package com.nutrisport.comidas.entidad;

/**
 * Momento del día de una comida (CHECK ck_registro_comida_tipo_comida de V5). Es el enum compartido por
 * todo el modelo: comidas, menús y recetas (§6.1).
 */
public enum TipoComida {
    DESAYUNO,
    MEDIA_MANANA,
    COMIDA,
    MERIENDA,
    CENA,
    PRE_ENTRENO,
    POST_ENTRENO,
    OTRO
}
