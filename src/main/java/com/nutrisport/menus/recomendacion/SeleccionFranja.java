package com.nutrisport.menus.recomendacion;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.entidad.Receta;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Receta elegida (o bloqueada por el usuario) para una franja del día.
 *
 * @param raciones  raciones de la receta (el motor elige entre 0,75 · 1 · 1,25 · 1,5 · 2)
 * @param bloqueada si la fijó el usuario: el motor la respeta y descuenta su energía del día
 */
public record SeleccionFranja(TipoComida tipoComida, Receta receta, BigDecimal raciones, boolean bloqueada) {

    public SeleccionFranja {
        Objects.requireNonNull(tipoComida, "tipoComida");
        Objects.requireNonNull(receta, "receta");
        Objects.requireNonNull(raciones, "raciones");
    }

    /** Nutrientes de la receta por ración × raciones, sin redondear; cero si la receta no tiene cálculo. */
    public Nutrientes nutrientes() {
        return PuntuadorRecetas.nutrientesPorRacion(receta).escalar(raciones);
    }
}
