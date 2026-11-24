package com.nutrisport.catalogo.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.comun.dto.Nutrientes;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Cálculo nutricional con programación convencional (§4.2): siempre a partir de los valores por 100 g
 * (o 100 ml) del catálogo, nunca con IA.
 */
@Component
public class CalculadoraNutricional {

    /** Nutrientes de {@code gramos} del alimento: valor100 × gramos / 100, sin redondear. */
    public Nutrientes deAlimento(Alimento alimento, BigDecimal gramos) {
        return new Nutrientes(alimento.getKcal100(), alimento.getProteinas100(), alimento.getCarbohidratos100(),
                alimento.getGrasas100())
                .escalar(gramos.movePointLeft(2)); // gramos / 100, división exacta
    }
}
