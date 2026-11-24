package com.nutrisport.actividad;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.actividad.entidad.Intensidad;
import com.nutrisport.actividad.servicio.CalculadoraGasto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Gasto neto de un entrenamiento: (MET − 1) × peso × horas × factor de intensidad, redondeado a entero. */
class CalculadoraGastoTest {

    private static final BigDecimal MET_CARRERA_9_7KMH = new BigDecimal("9.8");
    private static final BigDecimal PESO = new BigDecimal("70");

    private final CalculadoraGasto calculadora = new CalculadoraGasto();

    @Test
    void sesentaMinutosAIntensidadMedia() {
        // (9,8 − 1) × 70 × 1 × 1,00 = 616
        assertThat(calculadora.kcalNetas(MET_CARRERA_9_7KMH, PESO, 60, Intensidad.MEDIA)).isEqualByComparingTo("616");
    }

    @Test
    void sesentaMinutosAIntensidadAlta() {
        // 616 × 1,15 = 708,4 → 708
        assertThat(calculadora.kcalNetas(MET_CARRERA_9_7KMH, PESO, 60, Intensidad.ALTA)).isEqualByComparingTo("708");
    }

    @Test
    void treintaMinutosAIntensidadBaja() {
        // 8,8 × 70 × 0,5 × 0,85 = 261,8 → 262
        assertThat(calculadora.kcalNetas(MET_CARRERA_9_7KMH, PESO, 30, Intensidad.BAJA)).isEqualByComparingTo("262");
    }

    @Test
    void elResultadoEsUnEnteroSinDecimales() {
        assertThat(calculadora.kcalNetas(MET_CARRERA_9_7KMH, PESO, 30, Intensidad.BAJA).scale()).isZero();
    }
}
