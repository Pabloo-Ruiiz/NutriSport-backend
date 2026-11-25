package com.nutrisport.seguimiento.servicio;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.seguimiento.dto.RelacionNutricionResponse;
import com.nutrisport.seguimiento.servicio.EstadisticasServicio.Dia;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alimentación en días de entrenamiento frente a días de descanso (§3.G, Ampliación). Un día es de entreno si tiene
 * algún entrenamiento planificado o realizado (los omitidos no cuentan), en la zona horaria de la aplicación. Las
 * medias y los recuentos de días solo usan los días con alguna comida registrada, como las estadísticas.
 * <p>
 * Proteína post-entreno: una sesión la cumple si las comidas con hora registradas entre el final del entreno
 * (inicio + duración) y las 2 h siguientes suman al menos 25 g de proteína.
 */
@Service
@Transactional
public class RelacionNutricionActividadServicio {

    static final BigDecimal PROTEINA_POST_MINIMA_G = new BigDecimal("25");
    static final Duration VENTANA_POST = Duration.ofHours(2);

    private final EstadisticasServicio estadisticasServicio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final RegistroComidaRepositorio registroRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;

    public RelacionNutricionActividadServicio(EstadisticasServicio estadisticasServicio,
            EntrenamientoRepositorio entrenamientoRepositorio, RegistroComidaRepositorio registroRepositorio,
            SeguridadUtils seguridadUtils, Clock reloj) {
        this.estadisticasServicio = estadisticasServicio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.registroRepositorio = registroRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    /**
     * @throws com.nutrisport.comun.excepcion.PeticionInvalidaException 400 si hasta es anterior a desde o el rango
     *                                                                  pasa de 366 días
     */
    public RelacionNutricionResponse calcular(LocalDate desde, LocalDate hasta) {
        EstadisticasServicio.validarRango(desde, hasta);
        UUID usuarioId = seguridadUtils.usuarioActualId();
        ZoneId zona = reloj.getZone();
        OffsetDateTime inicio = desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        Set<LocalDate> diasConEntreno = entrenamientoRepositorio
                .actividadPorFecha(usuarioId, inicio, fin, zona.getId()).stream()
                .map(EntrenamientoRepositorio.ActividadDia::getFecha)
                .collect(Collectors.toSet());
        List<Dia> conRegistro = estadisticasServicio.dias(usuarioId, desde, hasta).stream()
                .filter(Dia::conRegistro)
                .toList();
        List<Dia> entreno = conRegistro.stream().filter(dia -> diasConEntreno.contains(dia.fecha())).toList();
        List<Dia> descanso = conRegistro.stream().filter(dia -> !diasConEntreno.contains(dia.fecha())).toList();

        List<Entrenamiento> sesiones = entrenamientoRepositorio.buscarEntre(usuarioId, inicio, fin).stream()
                .filter(sesion -> sesion.getEstado() != EstadoEntrenamiento.OMITIDO)
                .toList();
        // Las comidas del día siguiente cuentan para un entreno que termina cerca de medianoche
        List<RegistroComida> comidas = registroRepositorio
                .buscar(usuarioId, desde, hasta.plusDays(1), null, Pageable.unpaged()).getContent();
        int conProteinaPost = (int) sesiones.stream()
                .filter(sesion -> proteinaPost(sesion, comidas, zona).compareTo(PROTEINA_POST_MINIMA_G) >= 0)
                .count();

        return new RelacionNutricionResponse(desde, hasta,
                EstadisticasServicio.media(entreno.stream().map(Dia::consumido).toList()),
                objetivoMedio(entreno),
                EstadisticasServicio.media(descanso.stream().map(Dia::consumido).toList()),
                objetivoMedio(descanso),
                entreno.size(), descanso.size(), conProteinaPost, sesiones.size());
    }

    /** Proteína de las comidas con hora registradas en [final del entreno, final + 2 h]. */
    static BigDecimal proteinaPost(Entrenamiento sesion, List<RegistroComida> comidas, ZoneId zona) {
        Instant finEntreno = sesion.getFechaHora().toInstant().plus(Duration.ofMinutes(sesion.getDuracionMin()));
        Instant finVentana = finEntreno.plus(VENTANA_POST);
        return comidas.stream()
                .filter(comida -> comida.getHora() != null)
                .filter(comida -> {
                    Instant momento = comida.getFecha().atTime(comida.getHora()).atZone(zona).toInstant();
                    return !momento.isBefore(finEntreno) && !momento.isAfter(finVentana);
                })
                .map(RegistroComida::getTotalProteina)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Media de los objetivos de esos días; nula si ninguno tiene objetivo. */
    private static Nutrientes objetivoMedio(List<Dia> dias) {
        List<Nutrientes> objetivos = dias.stream().map(Dia::objetivo).filter(objetivo -> objetivo != null).toList();
        return objetivos.isEmpty() ? null : EstadisticasServicio.media(objetivos);
    }
}
