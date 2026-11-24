package com.nutrisport.comidas.dto;

import com.nutrisport.comun.dto.Nutrientes;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alimento del catálogo que encaja con un alimento detectado en la foto (§4.3).
 *
 * @param alimentoId id del alimento del catálogo
 * @param nombre     nombre en el catálogo
 * @param similitud  parecido del nombre (pg_trgm), de 0 a 1
 * @param estimados  nutrientes de los gramos detectados, calculados con el catálogo (nunca por la IA)
 * @param fuente     procedencia de los datos del alimento (trazabilidad)
 */
public record CoincidenciaCatalogo(UUID alimentoId, String nombre, BigDecimal similitud, Nutrientes estimados,
        String fuente) {
}
