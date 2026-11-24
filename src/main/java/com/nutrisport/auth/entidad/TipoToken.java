package com.nutrisport.auth.entidad;

/** Tipo de token de un solo uso enviado por email (CHECK ck_token_usuario_tipo de V1). */
public enum TipoToken {
    VERIFICACION_EMAIL,
    RECUPERACION_PASSWORD,
    /** El nuevo email se guarda en {@code datoExtra}. */
    CAMBIO_EMAIL
}
