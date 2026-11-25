package com.nutrisport.compra.entidad;

/** Estado de una lista de la compra (§6.1, CHECK ck_lista_compra_estado de V16). */
public enum EstadoLista {

    /** En uso. */
    ACTIVA,

    /** La compra ya está hecha; se conserva en el historial. */
    COMPLETADA
}
