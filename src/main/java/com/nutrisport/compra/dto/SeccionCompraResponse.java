package com.nutrisport.compra.dto;

import java.util.List;

/** Productos de una sección del supermercado (§3.E): primero los pendientes y al final los comprados. */
public record SeccionCompraResponse(String nombre, List<ProductoCompraResponse> productos) {
}
