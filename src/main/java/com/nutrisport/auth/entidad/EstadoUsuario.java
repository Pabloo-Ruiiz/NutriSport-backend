package com.nutrisport.auth.entidad;

/** Estado de la cuenta (CHECK ck_usuario_estado de V1). La verificación del email es un dato aparte. */
public enum EstadoUsuario {
    ACTIVO,
    /** Solo lo pone un administrador. */
    BLOQUEADO,
    /** Borrado lógico; el borrado físico se hace a los 30 días. */
    ELIMINADO
}
