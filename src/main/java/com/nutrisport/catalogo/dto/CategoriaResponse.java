package com.nutrisport.catalogo.dto;

import com.nutrisport.catalogo.entidad.TipoCategoria;
import java.util.UUID;

/** Categoría de alimentos o recetas (GET /api/v1/categorias, §7.3). */
public record CategoriaResponse(
        UUID id,
        String nombre,
        TipoCategoria tipo,
        UUID padreId,
        String seccionCompra,
        int orden) {
}
