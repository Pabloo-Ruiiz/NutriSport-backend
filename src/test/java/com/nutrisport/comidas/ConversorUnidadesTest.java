package com.nutrisport.comidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.comidas.servicio.ConversorUnidades;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Conversión de la cantidad introducida por el usuario a gramos (o mililitros). */
class ConversorUnidadesTest {

    private final ConversorUnidades conversor = new ConversorUnidades();

    @Test
    void gramos() {
        assertThat(conversor.aGramos(platano(), new BigDecimal("150"), "g")).isEqualByComparingTo("150");
    }

    @Test
    void kilogramos() {
        assertThat(conversor.aGramos(platano(), new BigDecimal("0.2"), "kg")).isEqualByComparingTo("200");
    }

    @Test
    void porcionCaseraSinDistinguirMayusculasNiTildes() {
        assertThat(conversor.aGramos(platano(), BigDecimal.ONE, "Unidad mediana")).isEqualByComparingTo("120");
        assertThat(conversor.aGramos(platano(), BigDecimal.ONE, "UNIDAD MÉDIANA")).isEqualByComparingTo("120");
    }

    @Test
    void mililitrosDeUnAlimentoQueSeMideEnMl() {
        assertThat(conversor.aGramos(leche(), new BigDecimal("250"), "ml")).isEqualByComparingTo("250");
    }

    @Test
    void mililitrosDeUnAlimentoQueSeMideEnGramosNoSeAdmiten() {
        assertThatThrownBy(() -> conversor.aGramos(platano(), new BigDecimal("250"), "ml"))
                .isInstanceOf(PeticionInvalidaException.class);
    }

    @Test
    void unaPorcionQueElAlimentoNoTieneLanzaUnErrorEnElCampoUnidad() {
        assertThatThrownBy(() -> conversor.aGramos(platano(), BigDecimal.ONE, "taza"))
                .isInstanceOfSatisfying(PeticionInvalidaException.class, error ->
                        assertThat(error.getErrores()).singleElement()
                                .satisfies(campo -> assertThat(campo.campo()).isEqualTo("unidad")));
    }

    private static Alimento platano() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Plátano");
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setPorciones(List.of(new PorcionCasera("unidad mediana", new BigDecimal("120"))));
        return alimento;
    }

    private static Alimento leche() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Leche semidesnatada");
        alimento.setUnidadBase(UnidadBase.ML);
        return alimento;
    }
}
