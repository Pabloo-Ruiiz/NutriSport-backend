package com.nutrisport.asistente.entidad;

/** Quién escribe un mensaje de una conversación con el asistente (CHECK ck_mensaje_ia_rol de V18). */
public enum RolMensaje {

    /** El deportista. */
    USUARIO,

    /** La respuesta del asistente (o la respuesta segura del filtro de temas sensibles). */
    ASISTENTE,

    /** Resultado de una herramienta, si se decide guardarlo aparte. */
    HERRAMIENTA
}
