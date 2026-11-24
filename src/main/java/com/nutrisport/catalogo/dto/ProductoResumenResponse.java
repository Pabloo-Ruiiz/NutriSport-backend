package com.nutrisport.catalogo.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado de la búsqueda de productos comerciales (§7.3 y §7.11). Los resultados de búsqueda no se guardan:
 * un producto se guarda en el catálogo al abrir su ficha ({@code GET /productos/{codigoBarras}}).
 *
 * @param id                id del alimento si ese producto ya está en el catálogo; nulo si no
 * @param kcal100           kcal por 100 g o 100 ml; nulo si el proveedor no la informa
 * @param nutricionCompleta si trae energía y los tres macronutrientes (sin ellos no se puede registrar)
 */
public record ProductoResumenResponse(
        UUID id,
        String codigoBarras,
        String nombre,
        String marca,
        String imagenUrl,
        BigDecimal kcal100,
        boolean nutricionCompleta,
        FuenteResponse fuente) {
}
