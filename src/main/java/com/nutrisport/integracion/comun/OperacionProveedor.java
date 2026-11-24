package com.nutrisport.integracion.comun;

/**
 * Tipo de llamada a un proveedor, para los límites de {@link LimitadorProveedor}. Open Food Facts tiene
 * límites distintos para productos y búsquedas; TheMealDB y USDA comparten un solo límite para todas sus
 * llamadas, así que con ellos la operación no cambia el cupo.
 */
public enum OperacionProveedor {

    /** Un producto por código o por identificador (Open Food Facts: {@code /api/v2/product/{codigo}.json}). */
    PRODUCTO,

    /** Búsqueda por texto o por filtros. */
    BUSQUEDA,

    /** Cualquier otra consulta (detalle, listas, categorías...). */
    CONSULTA
}
