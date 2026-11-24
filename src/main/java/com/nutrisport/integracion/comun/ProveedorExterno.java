package com.nutrisport.integracion.comun;

/**
 * APIs externas de alimentos y recetas (Ampliación, docs/API_INTEGRATIONS.md). Identifica al proveedor en
 * los límites de peticiones ({@link LimitadorProveedor}) y en su disponibilidad ({@link EstadoProveedor}).
 * <p>
 * No es la procedencia guardada en {@code alimento_fuente}: allí se usa {@code FuenteDato}, que incluye
 * BEDCA (se importa por CSV, no es una API) y no incluye TheMealDB (aporta recetas, no alimentos).
 */
public enum ProveedorExterno {
    OPEN_FOOD_FACTS,
    THEMEALDB,
    USDA
}
