package com.nutrisport.seguimiento.servicio;

import com.nutrisport.seguimiento.repositorio.InsightRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea nocturna de insights (§4.6): cada día a las 03:00 (zona {@code app.zona-horaria}) busca patrones para los
 * usuarios activos con los insights activados, el consentimiento de salud vigente y alguna comida, entrenamiento o
 * pesaje en los últimos 14 días ({@link InsightRepositorio#usuariosParaDetectar}). Los recorre en lotes de 100 por
 * orden de id y un error con un usuario se registra y no detiene a los demás.
 */
@Component
public class InsightsTarea {

    static final int TAMANO_LOTE = 100;
    static final int DIAS_ACTIVIDAD = 14;

    private static final UUID PRIMERO = new UUID(0L, 0L);
    private static final Logger LOG = LoggerFactory.getLogger(InsightsTarea.class);

    /**
     * Resumen de una ejecución.
     *
     * @param usuarios usuarios procesados (con o sin insights nuevos)
     * @param insights insights guardados en total
     * @param errores  usuarios en los que falló la detección
     */
    public record Resultado(int usuarios, int insights, int errores) {
    }

    private final InsightRepositorio insightRepositorio;
    private final InsightServicio insightServicio;
    private final Clock reloj;

    public InsightsTarea(InsightRepositorio insightRepositorio, InsightServicio insightServicio, Clock reloj) {
        this.insightRepositorio = insightRepositorio;
        this.insightServicio = insightServicio;
        this.reloj = reloj;
    }

    /** Ejecución programada: cada día a las 03:00, con el día que acaba de empezar. */
    @Scheduled(cron = "0 0 3 * * *", zone = "${app.zona-horaria}")
    public void ejecutarProgramada() {
        ejecutar(LocalDate.now(reloj));
    }

    /**
     * @param hoy día de la detección: analiza los 28 días anteriores y busca usuarios con actividad desde hoy − 14
     */
    public Resultado ejecutar(LocalDate hoy) {
        LocalDate desde = hoy.minusDays(DIAS_ACTIVIDAD);
        int usuarios = 0;
        int insights = 0;
        int errores = 0;
        UUID ultimo = PRIMERO;
        List<UUID> lote;
        do {
            lote = insightRepositorio.usuariosParaDetectar(desde,
                    desde.atStartOfDay(reloj.getZone()).toOffsetDateTime(), ultimo, TAMANO_LOTE);
            for (UUID usuarioId : lote) {
                try {
                    insights += insightServicio.generarPara(usuarioId, hoy).size();
                    usuarios++;
                } catch (RuntimeException e) {
                    errores++;
                    LOG.warn("No se han podido generar los insights del usuario {}: {}", usuarioId, e.toString());
                }
            }
            if (!lote.isEmpty()) {
                ultimo = lote.get(lote.size() - 1);
            }
        } while (lote.size() == TAMANO_LOTE);
        LOG.info("Tarea nocturna de insights: {} usuarios, {} insights, {} errores", usuarios, insights, errores);
        return new Resultado(usuarios, insights, errores);
    }
}
