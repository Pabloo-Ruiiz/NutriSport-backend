package com.nutrisport.comun.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Similitud de texto para emparejar ingredientes, con los ejemplos de docs/API_INTEGRATIONS.md §7.2 y el umbral de
 * 0,6 de MapeoIngredientesServicio.
 */
class SimilitudTextoTest {

    private static final BigDecimal UMBRAL = new BigDecimal("0.60");

    @Test
    void lasDescripcionesLargasDeUsdaConTodasLasPalabrasSuperanElUmbral() {
        assertThat(SimilitudTexto.confianza("Chicken Breast", "Chicken, broilers or fryers, breast, meat only, raw"))
                .isGreaterThanOrEqualTo(UMBRAL);
        assertThat(SimilitudTexto.confianza("Olive Oil", "Oil, olive, salad or cooking")).isGreaterThanOrEqualTo(UMBRAL);
        assertThat(SimilitudTexto.confianza("Banana", "Bananas, raw")).isGreaterThanOrEqualTo(UMBRAL);
        assertThat(SimilitudTexto.confianza("Chickpeas", "Chickpeas (garbanzo beans, bengal gram), mature seeds, raw"))
                .isGreaterThanOrEqualTo(UMBRAL);
    }

    @Test
    void sinEmparejamientoFiableNoLlegaAlUmbral() {
        // El caso de la harissa en §7.2
        assertThat(SimilitudTexto.confianza("Harissa Spice", "Spices, pepper, red or cayenne")).isLessThan(UMBRAL);
        assertThat(SimilitudTexto.confianza("Chicken Breast", "Beef, ground, raw")).isLessThan(UMBRAL);
    }

    @Test
    void textosIgualesValenUnoYSinPalabrasCero() {
        assertThat(SimilitudTexto.confianza("Pechuga de pollo", "pechuga de POLLO")).isEqualByComparingTo("1.00");
        assertThat(SimilitudTexto.confianza("Plátano", "platano")).isEqualByComparingTo("1.00");
        assertThat(SimilitudTexto.confianza("", "Bananas, raw")).isEqualByComparingTo("0.00");
        assertThat(SimilitudTexto.confianza("Banana", null)).isEqualByComparingTo("0.00");
    }

    @Test
    void losPluralesSencillosCuentanComoLaMismaPalabra() {
        assertThat(SimilitudTexto.mismaPalabra("spice", "spices")).isTrue();
        assertThat(SimilitudTexto.mismaPalabra("tomato", "tomatoes")).isTrue();
        assertThat(SimilitudTexto.mismaPalabra("oil", "oil")).isTrue();
        assertThat(SimilitudTexto.mismaPalabra("pea", "peanut")).isFalse();
        assertThat(SimilitudTexto.mismaPalabra("an", "and")).isFalse();
    }
}
