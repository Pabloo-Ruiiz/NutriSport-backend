package com.nutrisport.seguimiento.entidad;

/** Patrones que detectan las reglas de §4.6 (CHECK ck_insight_tipo de V17). */
public enum TipoInsight {

    /** Proteína por debajo del 80 % del objetivo en al menos 4 de los últimos 7 días con registro. */
    PROTEINA_BAJA,

    /** En los días de entreno, hidratos medios al menos un 15 % por debajo del objetivo (mínimo 3 días). */
    CARBOHIDRATOS_ENTRENO_BAJOS,

    /** Al menos 25 g de proteína en 3 o más comidas en al menos el 75 % de los días con registro. */
    REPARTO_PROTEINA_BUENO,

    /** Un día de la semana con al menos un 30 % menos registros que la media. */
    DIAS_SIN_REGISTRO,

    /** Pendiente del peso (al menos 4 pesajes) en sentido contrario al del objetivo. */
    TENDENCIA_PESO
}
