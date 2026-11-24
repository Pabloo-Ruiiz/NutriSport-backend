package com.nutrisport.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.comun.dto.Nutrientes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Cálculo de nutrientes a partir de los valores por 100 g del catálogo. */
class CalculadoraNutricionalTest {

    private final CalculadoraNutricional calculadora = new CalculadoraNutricional();

    @Test
    void cientoCincuentaGramosDeUnAlimentoDe130KcalSon195Kcal() {
        Nutrientes nutrientes = calculadora.deAlimento(alimento("130", "2.7", "28.0", "0.3"), new BigDecimal("150"));

        assertThat(nutrientes.kcal()).isEqualByComparingTo("195");
    }

    @Test
    void losCuatroNutrientesSeEscalanIgual() {
        Nutrientes nutrientes = calculadora.deAlimento(alimento("130", "2.7", "28.0", "0.3"), new BigDecimal("150"));

        // × 1,5 en los cuatro
        assertThat(nutrientes.kcal()).isEqualByComparingTo("195");
        assertThat(nutrientes.proteina()).isEqualByComparingTo("4.05");
        assertThat(nutrientes.carbohidratos()).isEqualByComparingTo("42");
        assertThat(nutrientes.grasa()).isEqualByComparingTo("0.45");
    }

    private static Alimento alimento(String kcal, String proteinas, String carbohidratos, String grasas) {
        Alimento alimento = new Alimento();
        alimento.setNombre("Arroz blanco cocido");
        alimento.setKcal100(new BigDecimal(kcal));
        alimento.setProteinas100(new BigDecimal(proteinas));
        alimento.setCarbohidratos100(new BigDecimal(carbohidratos));
        alimento.setGrasas100(new BigDecimal(grasas));
        return alimento;
    }
}
