package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/**
 * Un proveedor externo (Open Food Facts, TheMealDB, USDA) no está disponible: está caído, ha limitado las
 * peticiones o se ha agotado el cupo propio (docs/API_INTEGRATIONS.md §6.3 y §6.4). 503
 * {@code PROVEEDOR_NO_DISPONIBLE} con la cabecera {@code Retry-After} ({@link #getSegundosReintento()}).
 * <p>
 * El mensaje propone una alternativa al usuario; nunca incluye detalles de la respuesta del proveedor.
 */
public class ProveedorNoDisponibleException extends ApiException {

    public static final String MENSAJE =
            "La búsqueda de productos comerciales no está disponible en este momento. Busca otro producto o "
                    + "regístralo manualmente";

    private final long segundosReintento;

    public ProveedorNoDisponibleException(long segundosReintento) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PROVEEDOR_NO_DISPONIBLE", MENSAJE);
        this.segundosReintento = Math.max(1, segundosReintento);
    }

    public long getSegundosReintento() {
        return segundosReintento;
    }
}
