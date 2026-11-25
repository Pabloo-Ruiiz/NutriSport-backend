package com.nutrisport.seguimiento.dto;

import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.menus.dto.ComidaPendienteResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen de un día (GET /api/v1/seguimiento/resumen, §3.F y §7.7): lo consumido frente al objetivo de
 * ese día (guardado en ObjetivoDiario), con sus comidas y entrenamientos.
 *
 * @param fecha                         día del resumen
 * @param objetivo                      objetivo total del día (base + entrenamientos)
 * @param kcalEntreno                   gasto neto de los entrenamientos que cuentan
 * @param consumido                     suma de las comidas del día
 * @param restante                      objetivo − consumido por nutriente, nunca negativo
 * @param excesoKcal                    kcal consumidas por encima del objetivo; nulo si no se supera
 * @param porcentajes                   consumido / objetivo × 100 por nutriente, entero (puede pasar de 100)
 * @param comidas                       comidas del día en orden cronológico
 * @param entrenamientos                entrenamientos del día en orden cronológico
 * @param comidasPlanificadasPendientes comidas planificadas de ese día aún no consumidas en los menús ACTIVO
 *                                      (Ampliación), en el orden de sus menús
 */
public record ResumenDiarioResponse(
        LocalDate fecha,
        Nutrientes objetivo,
        BigDecimal kcalEntreno,
        Nutrientes consumido,
        Nutrientes restante,
        BigDecimal excesoKcal,
        Porcentajes porcentajes,
        List<RegistroComidaResponse> comidas,
        List<EntrenamientoResponse> entrenamientos,
        List<ComidaPendienteResponse> comidasPlanificadasPendientes) {

    public ResumenDiarioResponse {
        comidas = List.copyOf(comidas);
        entrenamientos = List.copyOf(entrenamientos);
        comidasPlanificadasPendientes = List.copyOf(comidasPlanificadasPendientes);
    }

    /** Porcentaje del objetivo consumido de cada nutriente, redondeado a entero. */
    public record Porcentajes(int kcal, int proteina, int carbohidratos, int grasa) {
    }
}
