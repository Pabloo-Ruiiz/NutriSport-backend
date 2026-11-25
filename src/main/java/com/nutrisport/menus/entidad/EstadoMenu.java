package com.nutrisport.menus.entidad;

/** Estado de un menú (§6.1). */
public enum EstadoMenu {

    /** Recién generado o en edición. */
    BORRADOR,

    /** El que el usuario está siguiendo. */
    ACTIVO,

    /** Ya pasado o descartado; se conserva en el historial. */
    ARCHIVADO
}
