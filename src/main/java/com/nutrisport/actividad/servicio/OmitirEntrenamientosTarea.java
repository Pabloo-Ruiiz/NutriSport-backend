package com.nutrisport.actividad.servicio;

import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarea nocturna (§3.G): marca como OMITIDO los entrenamientos PLANIFICADO de días anteriores a hoy
 * que no se han marcado como realizados, y recalcula el objetivo de esos días, porque dejan de contar.
 * <p>
 * Se ejecuta cada día a las 00:30 en la zona {@code app.zona-horaria} (SchedulingConfig). Recoge también
 * los de días más antiguos por si alguna noche no se ejecutó.
 */
@Component
public class OmitirEntrenamientosTarea {

    private static final Logger LOG = LoggerFactory.getLogger(OmitirEntrenamientosTarea.class);

    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final Clock reloj;

    public OmitirEntrenamientosTarea(EntrenamientoRepositorio entrenamientoRepositorio,
            ObjetivoDiarioServicio objetivoDiarioServicio, Clock reloj) {
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.reloj = reloj;
    }

    /**
     * @param hoy día en curso en la zona de la aplicación: se omiten los planificados anteriores a su
     *            inicio
     * @return número de entrenamientos marcados como omitidos
     */
    /** Ejecución programada: cada día a las 00:30, con el día que acaba de empezar. */
    @Scheduled(cron = "0 30 0 * * *", zone = "${app.zona-horaria}")
    public void ejecutarProgramada() {
        ejecutar(LocalDate.now(reloj));
    }

    @Transactional
    public int ejecutar(LocalDate hoy) {
        ZoneId zona = reloj.getZone();
        List<Entrenamiento> pendientes = entrenamientoRepositorio.findByEstadoAndFechaHoraBefore(
                EstadoEntrenamiento.PLANIFICADO, hoy.atStartOfDay(zona).toOffsetDateTime());
        Set<Map.Entry<UUID, LocalDate>> diasAfectados = new LinkedHashSet<>();
        for (Entrenamiento entrenamiento : pendientes) {
            entrenamiento.setEstado(EstadoEntrenamiento.OMITIDO);
            diasAfectados.add(Map.entry(entrenamiento.getUsuario().getId(),
                    entrenamiento.getFechaHora().atZoneSameInstant(zona).toLocalDate()));
        }
        entrenamientoRepositorio.flush();
        // Un usuario sin perfil no debe detener la tarea para los demás
        diasAfectados.forEach(dia -> objetivoDiarioServicio.recalcularSiHayPerfil(dia.getKey(), dia.getValue()));
        LOG.info("Tarea nocturna: {} entrenamientos planificados marcados como omitidos", pendientes.size());
        return pendientes.size();
    }
}
