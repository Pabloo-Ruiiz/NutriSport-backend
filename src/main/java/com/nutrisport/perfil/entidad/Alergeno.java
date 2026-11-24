package com.nutrisport.perfil.entidad;

/**
 * Los 14 alérgenos de declaración obligatoria del Reglamento (UE) 1169/2011
 * (CHECK ck_preferencia_alimentaria_alergeno de V3). La celiaquía es una alergia a {@link #GLUTEN}.
 */
public enum Alergeno {
    GLUTEN,
    CRUSTACEOS,
    HUEVOS,
    PESCADO,
    CACAHUETES,
    SOJA,
    LACTEOS,
    FRUTOS_DE_CASCARA,
    APIO,
    MOSTAZA,
    SESAMO,
    SULFITOS,
    ALTRAMUCES,
    MOLUSCOS
}
