package com.nutrisport.seguimiento.servicio;

import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.seguimiento.dto.InsightResponse;
import com.nutrisport.seguimiento.entidad.Insight;
import com.nutrisport.seguimiento.repositorio.InsightRepositorio;
import com.nutrisport.seguimiento.repositorio.RegistroPesoRepositorio;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.DiaDatos;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.Pesaje;
import com.nutrisport.seguimiento.servicio.EstadisticasServicio.Dia;
import com.nutrisport.seguimiento.servicio.RedactorInsights.Redaccion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Insights de un usuario (§4.6, Ampliación): reúne los datos de los últimos 28 días completos (de hoy − 28 a ayer),
 * detecta los patrones con {@link DetectorPatrones}, los redacta con {@link RedactorInsights} y los guarda como los
 * únicos vigentes. La llamada a la IA (si la hay) va fuera de las transacciones: primero se calculan los datos (y se
 * crean los objetivos que falten), después se redacta y al final se guarda.
 * <p>
 * Un usuario con los insights desactivados no recibe ninguno.
 */
@Service
public class InsightServicio {

    static final int DIAS_PERIODO = 28;

    private record Deteccion(DatosPeriodo datos, List<Hallazgo> hallazgos) {
    }

    private final EstadisticasServicio estadisticasServicio;
    private final RegistroComidaRepositorio registroRepositorio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final RegistroPesoRepositorio pesoRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final InsightRepositorio insightRepositorio;
    private final RedactorInsights redactor;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;
    private final TransactionTemplate escritura;
    private final DetectorPatrones detector = new DetectorPatrones();

    public InsightServicio(EstadisticasServicio estadisticasServicio, RegistroComidaRepositorio registroRepositorio,
            EntrenamientoRepositorio entrenamientoRepositorio, RegistroPesoRepositorio pesoRepositorio,
            PerfilDeportivoRepositorio perfilRepositorio, UsuarioRepositorio usuarioRepositorio,
            InsightRepositorio insightRepositorio, RedactorInsights redactor, SeguridadUtils seguridadUtils,
            Clock reloj, PlatformTransactionManager transacciones) {
        this.estadisticasServicio = estadisticasServicio;
        this.registroRepositorio = registroRepositorio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.pesoRepositorio = pesoRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.insightRepositorio = insightRepositorio;
        this.redactor = redactor;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
    }

    /**
     * Detecta los patrones del periodo que termina ayer, marca como no vigentes los insights anteriores y guarda los
     * nuevos (si no hay ninguno, el usuario se queda sin vigentes).
     *
     * @param hoy día de la detección (en la tarea nocturna, el que acaba de empezar)
     * @return los insights guardados; vacía si no se detecta nada o el usuario los tiene desactivados (entonces no
     *         se toca nada)
     */
    public List<InsightResponse> generarPara(UUID usuarioId, LocalDate hoy) {
        Deteccion deteccion = escritura.execute(estado -> {
            if (!insightsActivos(usuarioId)) {
                return null;
            }
            DatosPeriodo datos = datosPeriodo(usuarioId, hoy);
            ObjetivoDeportivo objetivo = perfilRepositorio.findByUsuarioId(usuarioId)
                    .map(PerfilDeportivo::getObjetivo)
                    .orElse(null);
            return new Deteccion(datos, detector.detectar(datos, objetivo));
        });
        if (deteccion == null) {
            return List.of();
        }

        List<Redaccion> redacciones = deteccion.hallazgos().stream()
                .map(hallazgo -> redactor.redactar(hallazgo, usuarioId))
                .toList();

        return escritura.execute(estado -> {
            // Se pudieron desactivar mientras se redactaban
            if (!insightsActivos(usuarioId)) {
                return List.<InsightResponse>of();
            }
            insightRepositorio.marcarNoVigentes(usuarioId);
            Usuario usuario = usuarioRepositorio.getReferenceById(usuarioId);
            List<InsightResponse> guardados = new ArrayList<>();
            for (int i = 0; i < deteccion.hallazgos().size(); i++) {
                Hallazgo hallazgo = deteccion.hallazgos().get(i);
                Insight insight = new Insight();
                insight.setUsuario(usuario);
                insight.setTipo(hallazgo.tipo());
                insight.setSeveridad(hallazgo.severidad());
                insight.setTitulo(redacciones.get(i).titulo());
                insight.setTexto(redacciones.get(i).texto());
                insight.setDatos(hallazgo.datos());
                insight.setPeriodoDesde(deteccion.datos().desde());
                insight.setPeriodoHasta(deteccion.datos().hasta());
                guardados.add(respuesta(insightRepositorio.save(insight)));
            }
            return guardados;
        });
    }

    /** Insights vigentes del usuario, los más recientes primero. */
    @Transactional(readOnly = true)
    public List<InsightResponse> vigentes(UUID usuarioId) {
        return insightRepositorio.findByUsuarioIdAndVigenteTrueOrderByCreadoEnDesc(usuarioId).stream()
                .map(InsightServicio::respuesta)
                .toList();
    }

    /** Los del usuario autenticado. */
    @Transactional(readOnly = true)
    public List<InsightResponse> vigentes() {
        return vigentes(seguridadUtils.usuarioActualId());
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Los 28 días que terminan ayer: lo consumido y el objetivo guardado de cada día (se crean los que falten en los
     * días con comidas, como en las estadísticas), si hubo entreno, la proteína de cada comida y los pesajes.
     */
    DatosPeriodo datosPeriodo(UUID usuarioId, LocalDate hoy) {
        LocalDate desde = hoy.minusDays(DIAS_PERIODO);
        LocalDate hasta = hoy.minusDays(1);
        ZoneId zona = reloj.getZone();
        OffsetDateTime inicio = desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hoy.atStartOfDay(zona).toOffsetDateTime();

        List<Dia> dias = estadisticasServicio.dias(usuarioId, desde, hasta);
        Map<LocalDate, List<BigDecimal>> proteinaPorDia = registroRepositorio
                .buscar(usuarioId, desde, hasta, null, Pageable.unpaged()).stream()
                .collect(Collectors.groupingBy(RegistroComida::getFecha,
                        Collectors.mapping(RegistroComida::getTotalProteina, Collectors.toList())));
        Set<LocalDate> diasEntreno = entrenamientoRepositorio
                .actividadPorFecha(usuarioId, inicio, fin, zona.getId()).stream()
                .map(EntrenamientoRepositorio.ActividadDia::getFecha)
                .collect(Collectors.toSet());
        List<Pesaje> pesajes = pesoRepositorio.findByUsuarioIdAndFechaBetweenOrderByFechaAsc(usuarioId, desde, hasta)
                .stream()
                .map(pesaje -> new Pesaje(pesaje.getFecha(), pesaje.getPesoKg()))
                .toList();

        List<DiaDatos> diasDatos = dias.stream()
                .map(dia -> new DiaDatos(dia.fecha(), dia.consumido(), dia.objetivo(),
                        diasEntreno.contains(dia.fecha()), proteinaPorDia.getOrDefault(dia.fecha(), List.of())))
                .toList();
        return new DatosPeriodo(desde, hasta, diasDatos, pesajes);
    }

    private boolean insightsActivos(UUID usuarioId) {
        return usuarioRepositorio.findById(usuarioId).map(Usuario::isInsightsActivos).orElse(false);
    }

    private static InsightResponse respuesta(Insight insight) {
        return new InsightResponse(insight.getId(), insight.getTipo(), insight.getSeveridad(), insight.getTitulo(),
                insight.getTexto(), insight.getPeriodoDesde(), insight.getPeriodoHasta(), insight.getCreadoEn());
    }
}
