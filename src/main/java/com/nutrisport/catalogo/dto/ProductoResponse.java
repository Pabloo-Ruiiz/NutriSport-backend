package com.nutrisport.catalogo.dto;

import com.nutrisport.catalogo.entidad.UnidadBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ficha de un producto por código de barras (§7.3 y §7.11). Si el producto se ha podido guardar en el
 * catálogo lleva su {@code id}, que es el que se usa para registrarlo en una comida. Un producto con la
 * información nutricional incompleta se muestra igualmente, sin {@code id} y con
 * {@code nutricionCompleta = false}: no se puede añadir a una comida.
 *
 * @param id                 id del alimento en el catálogo; nulo si no se ha podido guardar
 * @param unidadBase         G (valores por 100 g) o ML (por 100 ml)
 * @param racion             ración del envase, si el proveedor la informa
 * @param alergenosContiene  constantes de {@code Alergeno}
 * @param alergenosTrazas    constantes de {@code Alergeno}
 * @param alergenosRevisados {@code false} hasta que un administrador los revisa
 */
public record ProductoResponse(
        UUID id,
        String codigoBarras,
        String nombre,
        String marca,
        String imagenUrl,
        UnidadBase unidadBase,
        Por100 por100,
        Racion racion,
        String ingredientesTexto,
        List<String> alergenosContiene,
        List<String> alergenosTrazas,
        boolean alergenosRevisados,
        boolean nutricionCompleta,
        FuenteResponse fuente) {

    public ProductoResponse {
        alergenosContiene = alergenosContiene == null ? List.of() : List.copyOf(alergenosContiene);
        alergenosTrazas = alergenosTrazas == null ? List.of() : List.copyOf(alergenosTrazas);
    }

    /** Valores por 100 g o 100 ml; nulos los que el proveedor no informa (nunca 0). */
    public record Por100(BigDecimal kcal, BigDecimal proteinas, BigDecimal carbohidratos, BigDecimal grasas,
            BigDecimal azucares, BigDecimal saturadas, BigDecimal fibra, BigDecimal sal) {
    }

    /** Ración del envase. */
    public record Racion(BigDecimal gramos, String descripcion) {
    }
}
