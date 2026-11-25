package com.nutrisport.seguimiento.servicio;

import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.seguimiento.dto.ComparativaResponse;
import com.nutrisport.seguimiento.dto.EstadisticasResponse;
import com.nutrisport.seguimiento.entidad.Agrupacion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodo actual frente al anterior (§3.F y §7.7, Ampliación): la semana (de lunes a hoy) frente a la semana anterior
 * completa, o el mes (del día 1 a hoy) frente al mes anterior completo. Se comparan medias diarias, así que los
 * periodos pueden tener distinta longitud. Las estadísticas van resumidas (sin serie).
 */
@Service
@Transactional
public class ComparativaServicio {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final EstadisticasServicio estadisticasServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;

    public ComparativaServicio(EstadisticasServicio estadisticasServicio, SeguridadUtils seguridadUtils, Clock reloj) {
        this.estadisticasServicio = estadisticasServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    /**
     * @param periodo SEMANA o MES; nulo = SEMANA
     * @throws PeticionInvalidaException 400 si el periodo es DIA
     */
    public ComparativaResponse comparar(Agrupacion periodo) {
        Agrupacion elegido = periodo == null ? Agrupacion.SEMANA : periodo;
        if (elegido == Agrupacion.DIA) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos",
                    List.of(new ErrorCampo("periodo", "El periodo debe ser SEMANA o MES")));
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate inicioActual = elegido == Agrupacion.SEMANA
                ? hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : hoy.withDayOfMonth(1);
        LocalDate inicioAnterior = elegido == Agrupacion.SEMANA ? inicioActual.minusWeeks(1)
                : inicioActual.minusMonths(1);

        EstadisticasResponse actual = resumida(
                estadisticasServicio.calcular(usuarioId, inicioActual, hoy, Agrupacion.DIA));
        EstadisticasResponse anterior = resumida(
                estadisticasServicio.calcular(usuarioId, inicioAnterior, inicioActual.minusDays(1), Agrupacion.DIA));
        return new ComparativaResponse(actual, anterior, variaciones(actual, anterior));
    }

    /** Variación de la media diaria de cada nutriente en % y de la adherencia en puntos. */
    static Map<String, BigDecimal> variaciones(EstadisticasResponse actual, EstadisticasResponse anterior) {
        Nutrientes ahora = actual.mediaDiaria();
        Nutrientes antes = anterior.mediaDiaria();
        Map<String, BigDecimal> variacion = new LinkedHashMap<>();
        variacion.put("kcal", variacionPct(ahora.kcal(), antes.kcal()));
        variacion.put("proteina", variacionPct(ahora.proteina(), antes.proteina()));
        variacion.put("carbohidratos", variacionPct(ahora.carbohidratos(), antes.carbohidratos()));
        variacion.put("grasa", variacionPct(ahora.grasa(), antes.grasa()));
        variacion.put("adherenciaPts",
                BigDecimal.valueOf(actual.porcentajeAdherencia() - anterior.porcentajeAdherencia()));
        return variacion;
    }

    /** (actual − anterior) / anterior × 100 con un decimal; nula si el anterior no es positivo. */
    static BigDecimal variacionPct(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() <= 0) {
            return null;
        }
        return actual.subtract(anterior).multiply(CIEN).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static EstadisticasResponse resumida(EstadisticasResponse completa) {
        return new EstadisticasResponse(completa.desde(), completa.hasta(), completa.agrupacion(), List.of(),
                completa.mediaDiaria(), completa.objetivoMedio(), completa.diasConRegistro(),
                completa.diasEnObjetivo(), completa.porcentajeAdherencia(), completa.repartoMacrosPct(),
                completa.kcalPorTipoComida(), completa.top());
    }
}
