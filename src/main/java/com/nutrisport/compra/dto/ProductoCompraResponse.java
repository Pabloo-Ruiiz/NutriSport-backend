package com.nutrisport.compra.dto;

import com.nutrisport.compra.entidad.EntradaDesglose;
import com.nutrisport.compra.entidad.UnidadCompra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Producto de una lista de la compra (§7.6).
 *
 * @param nombre   el del alimento o el texto libre
 * @param manual   añadido a mano (producto libre o desde una receta), no generado desde el menú
 * @param desglose gramos (o ml) por día del menú; vacío en los productos añadidos a mano
 */
public record ProductoCompraResponse(UUID id, String nombre, BigDecimal cantidad, UnidadCompra unidad, String seccion,
        boolean comprado, boolean manual, List<EntradaDesglose> desglose) {
}
