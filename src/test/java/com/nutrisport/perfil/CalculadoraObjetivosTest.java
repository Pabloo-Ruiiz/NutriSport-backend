package com.nutrisport.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.perfil.dto.ObjetivosCalculados;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import com.nutrisport.perfil.servicio.CalculadoraObjetivos;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Reglas del cálculo de objetivos base (paso 12 de la guía), con una fecha de referencia fija. */
class CalculadoraObjetivosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 21);

    private final CalculadoraObjetivos calculadora = new CalculadoraObjetivos();

    @Test
    void hombreModeradoQueMantieneElPeso() {
        ObjetivosCalculados resultado = calculadora.calcular(
                perfil(Sexo.HOMBRE, "1996-06-01", "70", "175", NivelActividad.MODERADO, ObjetivoDeportivo.MANTENER),
                HOY);

        assertThat(resultado.edad()).isEqualTo(30);
        // 10·70 + 6,25·175 − 5·30 + 5 = 1648,75; × 1,4 = 2308,25
        assertThat(resultado.metabolismoBasal()).isEqualByComparingTo("1648.75");
        assertThat(resultado.gastoSinEntreno()).isEqualByComparingTo("2308.25");
        assertThat(resultado.ajustePorcentaje()).isZero();
        assertThat(resultado.kcal()).isEqualByComparingTo("2308");
        assertThat(resultado.proteinaG()).isEqualByComparingTo("98");
        assertThat(resultado.grasaG()).isEqualByComparingTo("64");
        assertThat(resultado.carbohidratosG()).isEqualByComparingTo("335");
        assertThat(resultado.avisos()).isEmpty();
    }

    @Test
    void perderGrasaBajaLaEnergiaUn15PorCientoYSubeLaProteinaA2GramosPorKg() {
        ObjetivosCalculados resultado = calculadora.calcular(
                perfil(Sexo.HOMBRE, "1996-06-01", "70", "175", NivelActividad.MODERADO,
                        ObjetivoDeportivo.PERDER_GRASA),
                HOY);

        assertThat(resultado.ajustePorcentaje()).isEqualTo(-15);
        assertThat(resultado.kcal()).isEqualByComparingTo("1962");
        assertThat(resultado.proteinaG()).isEqualByComparingTo("140");
        assertThat(resultado.avisos()).isEmpty();
    }

    @Test
    void laEnergiaNuncaBajaDelMinimoDeSeguridadDeUnaMujer() {
        // 1096,5 × 1,2 = 1315,8; −15 % = 1118,43 < 1200
        ObjetivosCalculados resultado = calculadora.calcular(
                perfil(Sexo.MUJER, "2000-01-01", "45", "150", NivelActividad.SEDENTARIO,
                        ObjetivoDeportivo.PERDER_GRASA),
                HOY);

        assertThat(resultado.kcal()).isEqualByComparingTo("1200");
        assertThat(resultado.avisos()).hasSize(1);
        assertThat(resultado.avisos().get(0)).contains("mínimo de seguridad").contains("1200");
    }

    @Test
    void elDiaDelCumpleanosYaCuentaLaEdadNueva() {
        ObjetivosCalculados cumpleHoy = calculadora.calcular(
                perfil(Sexo.HOMBRE, "1996-11-21", "70", "175", NivelActividad.MODERADO, ObjetivoDeportivo.MANTENER),
                HOY);
        ObjetivosCalculados cumpleManana = calculadora.calcular(
                perfil(Sexo.HOMBRE, "1996-11-22", "70", "175", NivelActividad.MODERADO, ObjetivoDeportivo.MANTENER),
                HOY);

        assertThat(cumpleHoy.edad()).isEqualTo(30);
        assertThat(cumpleManana.edad()).isEqualTo(29);
    }

    private static PerfilDeportivo perfil(Sexo sexo, String fechaNacimiento, String pesoKg, String alturaCm,
            NivelActividad nivelActividad, ObjetivoDeportivo objetivo) {
        PerfilDeportivo perfil = new PerfilDeportivo();
        perfil.setSexo(sexo);
        perfil.setFechaNacimiento(LocalDate.parse(fechaNacimiento));
        perfil.setPesoActualKg(new BigDecimal(pesoKg));
        perfil.setAlturaCm(new BigDecimal(alturaCm));
        perfil.setNivelActividad(nivelActividad);
        perfil.setObjetivo(objetivo);
        return perfil;
    }
}
