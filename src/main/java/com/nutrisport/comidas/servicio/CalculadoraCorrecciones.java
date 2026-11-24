package com.nutrisport.comidas.servicio;

import com.nutrisport.comidas.dto.AlimentoConfirmadoRequest;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.CoincidenciaCatalogo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Calcula las correcciones del usuario frente a los candidatos del análisis. */
@Component
public class CalculadoraCorrecciones {

    private static final BigDecimal PORCENTAJE = new BigDecimal("100");
    private static final BigDecimal TOLERANCIA_SIN_CAMBIO = new BigDecimal("5");

    public Correcciones calcular(List<CandidatoAnalisis> candidatos,
            List<AlimentoConfirmadoRequest> alimentosConfirmados) {
        int aceptadosSinCambio = 0;
        int gramosModificados = 0;
        int alimentoCambiado = 0;
        int anadidos = 0;
        BigDecimal sumaErroresPct = BigDecimal.ZERO;
        int candidatosConError = 0;
        Set<Integer> conservados = new HashSet<>();

        for (AlimentoConfirmadoRequest confirmado : alimentosConfirmados) {
            Integer indice = confirmado.indiceCandidato();
            if (indice == null) {
                anadidos++;
                continue;
            }

            CandidatoAnalisis candidato = candidatos.get(indice);
            conservados.add(indice);
            BigDecimal gramosEstimados = candidato.gramosEstimados();
            BigDecimal diferencia = confirmado.gramos().subtract(gramosEstimados).abs();
            boolean gramosSinCambio = diferencia.multiply(PORCENTAJE)
                    .compareTo(gramosEstimados.multiply(TOLERANCIA_SIN_CAMBIO)) <= 0;
            CoincidenciaCatalogo primeraCoincidencia = candidato.coincidencias().isEmpty()
                    ? null
                    : candidato.coincidencias().get(0);
            boolean alimentoSinCambio = primeraCoincidencia != null
                    && primeraCoincidencia.alimentoId().equals(confirmado.alimentoId());

            if (gramosSinCambio && alimentoSinCambio) {
                aceptadosSinCambio++;
            }
            if (!gramosSinCambio) {
                gramosModificados++;
            }
            if (!alimentoSinCambio) {
                alimentoCambiado++;
            }

            sumaErroresPct = sumaErroresPct.add(diferencia.multiply(PORCENTAJE)
                    .divide(gramosEstimados, MathContext.DECIMAL128));
            candidatosConError++;
        }

        BigDecimal errorMedioGramosPct = candidatosConError == 0
                ? BigDecimal.ZERO.setScale(1)
                : sumaErroresPct.divide(BigDecimal.valueOf(candidatosConError), 1, RoundingMode.HALF_UP);
        return new Correcciones(aceptadosSinCambio, gramosModificados, alimentoCambiado,
                candidatos.size() - conservados.size(), anadidos, errorMedioGramosPct);
    }
}
