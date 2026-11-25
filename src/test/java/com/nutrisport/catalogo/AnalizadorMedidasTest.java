package com.nutrisport.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.catalogo.servicio.AnalizadorMedidas;
import com.nutrisport.catalogo.servicio.MedidaInterpretada;
import com.nutrisport.catalogo.servicio.TipoMedida;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Analizador de medidas de los ingredientes (docs/API_INTEGRATIONS.md §7.1), con las medidas reales de §4.2. */
class AnalizadorMedidasTest {

    @ParameterizedTest(name = "\"{0}\" → {1} {2} ({3})")
    @CsvSource(delimiter = '|', value = {
        // Medidas de "Chicken Couscous" (§4.2)
        "1 tbsp       | 1      | tbsp     | VOLUMEN",
        "1 chopped    | 1      | chopped  | UNIDAD",
        "200g         | 200    | g        | MASA",
        "pinch        | 0      | pinch    | DESPRECIABLE",
        "'2 tblsp '   | 2      | tbsp     | VOLUMEN",
        "10           | 10     | unidad   | UNIDAD",
        "220g         | 220    | g        | MASA",
        "200ml        | 200    | ml       | VOLUMEN",
        "Handful      | 1      | handful  | UNIDAD",
        // Fracciones ASCII y Unicode, mixtos y rangos
        "3/4 cup      | 0.75   | cup      | VOLUMEN",
        "½ tsp        | 0.5    | tsp      | VOLUMEN",
        "1½ cups      | 1.5    | cup      | VOLUMEN",
        "1 1/2 lbs    | 1.5    | lb       | MASA",
        "1-2 tbs      | 1.5    | tbsp     | VOLUMEN",
        "2 to 3 tablespoons | 2.5 | tbsp  | VOLUMEN",
        "1,5 kg       | 1.5    | kg       | MASA",
        // Variantes de unidades
        "2 Tablespoons | 2     | tbsp     | VOLUMEN",
        "1 teaspoon   | 1      | tsp      | VOLUMEN",
        "8 oz         | 8      | oz       | MASA",
        "500 grams    | 500    | g        | MASA",
        "1 litre      | 1      | l        | VOLUMEN",
        "3 cloves     | 3      | cloves   | UNIDAD",
        "2 large      | 2      | large    | UNIDAD",
        "tbsp         | 1      | tbsp     | VOLUMEN",
        "a handful    | 1      | handful  | UNIDAD",
        // Despreciables
        "to taste     | 0      | to taste | DESPRECIABLE",
        "Season to taste | 0   | to taste | DESPRECIABLE",
        "a pinch      | 0      | pinch    | DESPRECIABLE",
        "To garnish   | 0      | garnish  | DESPRECIABLE",
        // Tamaño del envase entre paréntesis
        "1 (12 oz.)   | 12     | oz       | MASA",
        "2 (400g) tins | 800   | g        | MASA",
    })
    void interpretaLasMedidas(String texto, String cantidad, String unidad, TipoMedida tipo) {
        MedidaInterpretada medida = AnalizadorMedidas.interpretar(texto);

        assertThat(medida.tipo()).isEqualTo(tipo);
        assertThat(medida.unidad()).isEqualTo(unidad);
        assertThat(medida.cantidad()).isEqualByComparingTo(cantidad);
    }

    @Test
    void loQueNoSeEntiendeEsDesconocido() {
        for (String texto : new String[] {"", "   ", "xyz", "some", "as required", "optional"}) {
            MedidaInterpretada medida = AnalizadorMedidas.interpretar(texto);
            assertThat(medida.tipo()).as(texto).isEqualTo(TipoMedida.DESCONOCIDA);
            assertThat(medida.cantidad()).as(texto).isNull();
        }
        assertThat(AnalizadorMedidas.interpretar(null).tipo()).isEqualTo(TipoMedida.DESCONOCIDA);
    }

    @Test
    void conversionesDeMasaYDeVolumen() {
        assertThat(AnalizadorMedidas.gramosDeMasa(AnalizadorMedidas.interpretar("1 lb"))).isEqualByComparingTo("453.6");
        assertThat(AnalizadorMedidas.gramosDeMasa(AnalizadorMedidas.interpretar("2 oz"))).isEqualByComparingTo("56.7");
        assertThat(AnalizadorMedidas.gramosDeMasa(AnalizadorMedidas.interpretar("0.5 kg"))).isEqualByComparingTo("500");
        // Densidad 1
        assertThat(AnalizadorMedidas.mililitros(AnalizadorMedidas.interpretar("2 tblsp"))).isEqualByComparingTo("30");
        assertThat(AnalizadorMedidas.mililitros(AnalizadorMedidas.interpretar("1 cup"))).isEqualByComparingTo("240");
        assertThat(AnalizadorMedidas.mililitros(AnalizadorMedidas.interpretar("200ml"))).isEqualByComparingTo("200");
        assertThat(AnalizadorMedidas.gramosDeMasa(AnalizadorMedidas.interpretar("1 cup"))).isNull();
    }

    @Test
    void soloLasHierbasHacenDespreciableUnPunado() {
        assertThat(AnalizadorMedidas.esHierba("Coriander")).isTrue();
        assertThat(AnalizadorMedidas.esHierba("fresh parsley")).isTrue();
        assertThat(AnalizadorMedidas.esHierba("Basil Leaves")).isTrue();
        assertThat(AnalizadorMedidas.esHierba("Peanuts")).isFalse();
        assertThat(AnalizadorMedidas.esHierba(null)).isFalse();
    }
}
