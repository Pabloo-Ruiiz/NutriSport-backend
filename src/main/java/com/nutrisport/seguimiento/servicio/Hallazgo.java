package com.nutrisport.seguimiento.servicio;

import com.nutrisport.seguimiento.entidad.SeveridadInsight;
import com.nutrisport.seguimiento.entidad.TipoInsight;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Patrón que ha detectado {@link DetectorPatrones}, con las cifras ya calculadas en {@code datos} (por ejemplo
 * {@code diferenciaPct = 18}, {@code gramosFaltantes = 63}): el texto del insight solo las inserta.
 *
 * @param datos cifras en el orden en que se calcularon (enteros, decimales con BigDecimal o textos); no se puede
 *              modificar
 */
public record Hallazgo(TipoInsight tipo, SeveridadInsight severidad, Map<String, Object> datos) {

    public Hallazgo {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(severidad, "severidad");
        datos = Collections.unmodifiableMap(new LinkedHashMap<>(datos == null ? Map.of() : datos));
    }
}
