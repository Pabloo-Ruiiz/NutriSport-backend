package com.nutrisport.comidas.servicio;

import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca como FALLIDO los análisis que llevan más de 2 minutos en PENDIENTE o PROCESANDO (§4.3, paso 5),
 * por ejemplo tras un reinicio del servidor o si la cola del ejecutor estaba llena. Se ejecuta cada
 * minuto (SchedulingConfig).
 */
@Component
public class AnalisisHuerfanosTarea {

    static final Duration PLAZO = Duration.ofMinutes(2);
    static final String MENSAJE = "El análisis ha tardado demasiado. Puedes registrar la comida manualmente";

    private static final Logger LOG = LoggerFactory.getLogger(AnalisisHuerfanosTarea.class);

    private final AnalisisImagenRepositorio analisisRepositorio;

    public AnalisisHuerfanosTarea(AnalisisImagenRepositorio analisisRepositorio) {
        this.analisisRepositorio = analisisRepositorio;
    }

    /** Ejecución programada: cada minuto. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void ejecutarProgramada() {
        ejecutar(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Devuelve cuántos análisis ha marcado como fallidos. */
    @Transactional
    public int ejecutar(OffsetDateTime ahora) {
        List<AnalisisImagen> atascados = analisisRepositorio.findByEstadoInAndCreadoEnBefore(
                List.of(EstadoAnalisis.PENDIENTE, EstadoAnalisis.PROCESANDO), ahora.minus(PLAZO));
        for (AnalisisImagen analisis : atascados) {
            analisis.setEstado(EstadoAnalisis.FALLIDO);
            analisis.setErrorMensaje(MENSAJE);
            analisis.setCompletadoEn(ahora);
        }
        if (!atascados.isEmpty()) {
            LOG.warn("{} análisis atascados marcados como fallidos", atascados.size());
        }
        return atascados.size();
    }
}
