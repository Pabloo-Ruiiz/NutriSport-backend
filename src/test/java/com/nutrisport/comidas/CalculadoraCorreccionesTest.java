package com.nutrisport.comidas;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.comidas.dto.AlimentoConfirmadoRequest;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.CoincidenciaCatalogo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comidas.servicio.CalculadoraCorrecciones;
import com.nutrisport.comidas.servicio.Correcciones;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculadoraCorreccionesTest {

    private final CalculadoraCorrecciones calculadora = new CalculadoraCorrecciones();

    @Test
    void calculaLasCorreccionesYElErrorMedioSoloConLosCandidatosConservados() {
        UUID alimentoPrimero = UUID.randomUUID();
        UUID alimentoSegundo = UUID.randomUUID();
        UUID alimentoTercero = UUID.randomUUID();
        List<CandidatoAnalisis> candidatos = List.of(
                candidato(alimentoPrimero, "100"),
                candidato(alimentoSegundo, "200"),
                candidato(alimentoTercero, "120"),
                candidato(UUID.randomUUID(), "400"));
        List<AlimentoConfirmadoRequest> confirmados = List.of(
                new AlimentoConfirmadoRequest(alimentoPrimero, new BigDecimal("100"), 0),
                new AlimentoConfirmadoRequest(alimentoSegundo, new BigDecimal("200"), 1),
                new AlimentoConfirmadoRequest(alimentoTercero, new BigDecimal("180"), 2),
                new AlimentoConfirmadoRequest(UUID.randomUUID(), new BigDecimal("50"), null));

        Correcciones resultado = calculadora.calcular(candidatos, confirmados);

        assertThat(resultado).isEqualTo(new Correcciones(2, 1, 0, 1, 1, new BigDecimal("16.7")));
    }

    @Test
    void noCalculaErrorMedioCuandoNoSeConservaNingunCandidato() {
        Correcciones resultado = calculadora.calcular(List.of(candidato(UUID.randomUUID(), "100")),
                List.of(new AlimentoConfirmadoRequest(UUID.randomUUID(), new BigDecimal("25"), null)));

        assertThat(resultado).isEqualTo(new Correcciones(0, 0, 0, 1, 1, new BigDecimal("0.0")));
    }

    private static CandidatoAnalisis candidato(UUID alimentoId, String gramos) {
        CoincidenciaCatalogo coincidencia = new CoincidenciaCatalogo(alimentoId, "Alimento",
                BigDecimal.ONE, Nutrientes.CERO, "CATALOGO");
        return new CandidatoAnalisis("Detectado", null, new BigDecimal(gramos), new BigDecimal("0.90"),
                "ALTA", false, List.of(coincidencia));
    }
}
