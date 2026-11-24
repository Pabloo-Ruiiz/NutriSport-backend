package com.nutrisport.ia.adaptador.simulado;

import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.ia.puerto.AlimentoDetectado;
import com.nutrisport.ia.puerto.ContextoReconocimiento;
import com.nutrisport.ia.puerto.ReconocimientoAlimentosPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reconocimiento simulado para desarrollo y tests (§4.3): devuelve siempre los mismos cuatro alimentos
 * que el mockup 03 (salmón a la plancha con arroz, brócoli y aguacate), sin llamar a nada externo.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "simulado", matchIfMissing = true)
public class ReconocimientoSimulado implements ReconocimientoAlimentosPuerto {

    static final String MODELO = "simulado";

    static final List<AlimentoDetectado> ALIMENTOS = List.of(
            new AlimentoDetectado("Salmón", Preparacion.PLANCHA, new BigDecimal("160"), new BigDecimal("0.94"), List.of()),
            new AlimentoDetectado("Arroz blanco", Preparacion.COCIDO, new BigDecimal("180"), new BigDecimal("0.88"), List.of()),
            new AlimentoDetectado("Brócoli", Preparacion.COCIDO, new BigDecimal("120"), new BigDecimal("0.71"), List.of()),
            new AlimentoDetectado("Aguacate", Preparacion.CRUDO, new BigDecimal("70"), new BigDecimal("0.42"), List.of()));

    @Override
    public ResultadoIa<List<AlimentoDetectado>> reconocer(byte[] jpeg, ContextoReconocimiento contexto) {
        return new ResultadoIa<>(ALIMENTOS, 0, 0, MODELO);
    }
}
