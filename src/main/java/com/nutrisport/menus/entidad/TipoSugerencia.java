package com.nutrisport.menus.entidad;

/** Lo único que puede proponer la IA para una receta de un menú (§4.5, CHECK ck_sugerencia_ia_tipo de V15). */
public enum TipoSugerencia {
    /** Cambiar los gramos de un ingrediente. */
    AJUSTE_CANTIDAD,
    /** Cambiar un ingrediente por un alimento del catálogo. */
    SUSTITUCION
}
