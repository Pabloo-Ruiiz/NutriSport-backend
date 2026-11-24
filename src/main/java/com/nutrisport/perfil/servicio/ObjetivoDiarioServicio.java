package com.nutrisport.perfil.servicio;

import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.entidad.ObjetivoDiario;
import com.nutrisport.perfil.entidad.OrigenObjetivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.repositorio.ObjetivoDiarioRepositorio;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Objetivo nutricional de cada día (§3.B, §3.G y §6.1 ObjetivoDiario):
 * <ul>
 * <li>Base: los objetivos del perfil en el momento de calcularlo.</li>
 * <li>Entrenamiento: suma del gasto neto de los entrenamientos PLANIFICADO y REALIZADO de ese día (en
 * la zona horaria de la aplicación); los OMITIDO no cuentan.</li>
 * <li>Total: base + entrenamiento; el extra se asigna entero a los carbohidratos (kcal / 4). La proteína
 * y la grasa no cambian.</li>
 * </ul>
 * Se crea o recalcula al registrar una comida de ese día y al crear, editar, cambiar de estado o borrar
 * un entrenamiento de esa fecha (aunque sea pasada). Un cambio de perfil solo recalcula hoy y los días
 * futuros, para que el historial y la adherencia no cambien.
 */
@Service
@Transactional
public class ObjetivoDiarioServicio {

    private static final BigDecimal KCAL_POR_G_CARBOHIDRATO = new BigDecimal("4");

    private final ObjetivoDiarioRepositorio objetivoRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final Clock reloj;

    public ObjetivoDiarioServicio(ObjetivoDiarioRepositorio objetivoRepositorio,
            PerfilDeportivoRepositorio perfilRepositorio, EntrenamientoRepositorio entrenamientoRepositorio,
            UsuarioRepositorio usuarioRepositorio, Clock reloj) {
        this.objetivoRepositorio = objetivoRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.reloj = reloj;
    }

    /**
     * Objetivo del día; si no existe, lo calcula y lo guarda.
     *
     * @throws ReglaNegocioException PERFIL_REQUERIDO si el usuario no tiene perfil
     */
    public ObjetivoDiarioResponse obtener(UUID usuarioId, LocalDate fecha) {
        ObjetivoDiario objetivo = objetivoRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha)
                .orElseGet(() -> calcularYGuardar(usuarioId, fecha, perfilRequerido(usuarioId)));
        return respuesta(objetivo);
    }

    /**
     * Vuelve a calcular el objetivo del día con el perfil y los entrenamientos actuales (lo crea si no
     * existía).
     *
     * @throws ReglaNegocioException PERFIL_REQUERIDO si el usuario no tiene perfil
     */
    public ObjetivoDiarioResponse recalcular(UUID usuarioId, LocalDate fecha) {
        return respuesta(calcularYGuardar(usuarioId, fecha, perfilRequerido(usuarioId)));
    }

    /**
     * Crea el objetivo del día si no existe y el usuario tiene perfil; si no tiene perfil no hace nada
     * (registrar comidas no exige perfil, §7.12).
     */
    public void crearSiNoExiste(UUID usuarioId, LocalDate fecha) {
        if (objetivoRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha).isPresent()) {
            return;
        }
        perfilRepositorio.findByUsuarioId(usuarioId)
                .filter(perfil -> perfil.getObjetivoKcal() != null)
                .ifPresent(perfil -> calcularYGuardar(usuarioId, fecha, perfil));
    }

    /** Como {@link #recalcular} pero sin error si el usuario no tiene perfil (para las tareas programadas). */
    public void recalcularSiHayPerfil(UUID usuarioId, LocalDate fecha) {
        perfilRepositorio.findByUsuarioId(usuarioId)
                .filter(perfil -> perfil.getObjetivoKcal() != null)
                .ifPresent(perfil -> calcularYGuardar(usuarioId, fecha, perfil));
    }

    /** Tras un cambio de perfil: recalcula los objetivos ya creados de hoy y de los días futuros. */
    public void recalcularDesdeHoy(UUID usuarioId) {
        PerfilDeportivo perfil = perfilRequerido(usuarioId);
        objetivoRepositorio.findByUsuarioIdAndFechaGreaterThanEqual(usuarioId, LocalDate.now(reloj))
                .forEach(objetivo -> calcular(objetivo, perfil));
    }

    // ---------------------------------------------------------------------------------------------
    // Cálculo
    // ---------------------------------------------------------------------------------------------

    private ObjetivoDiario calcularYGuardar(UUID usuarioId, LocalDate fecha, PerfilDeportivo perfil) {
        ObjetivoDiario objetivo = objetivoRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha).orElseGet(() -> {
            ObjetivoDiario nuevo = new ObjetivoDiario();
            nuevo.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
            nuevo.setFecha(fecha);
            return nuevo;
        });
        calcular(objetivo, perfil);
        return objetivoRepositorio.save(objetivo);
    }

    private void calcular(ObjetivoDiario objetivo, PerfilDeportivo perfil) {
        ZoneId zona = reloj.getZone();
        OffsetDateTime inicio = objetivo.getFecha().atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = objetivo.getFecha().plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        BigDecimal kcalEntreno = entrenamientoRepositorio
                .sumarKcalQueCuentan(objetivo.getUsuario().getId(), inicio, fin)
                .setScale(1, RoundingMode.HALF_UP);

        objetivo.setKcalBase(perfil.getObjetivoKcal());
        objetivo.setProteinaG(perfil.getObjetivoProteinaG());
        objetivo.setCarbohidratosG(perfil.getObjetivoCarbohidratosG());
        objetivo.setGrasaG(perfil.getObjetivoGrasaG());
        objetivo.setKcalEntreno(kcalEntreno);
        objetivo.setKcalTotal(perfil.getObjetivoKcal().add(kcalEntreno));
        objetivo.setCarbohidratosTotalG(perfil.getObjetivoCarbohidratosG()
                .add(kcalEntreno.divide(KCAL_POR_G_CARBOHIDRATO, 1, RoundingMode.HALF_UP)));
        objetivo.setOrigen(perfil.isObjetivosManuales() ? OrigenObjetivo.MANUAL : OrigenObjetivo.CALCULADO);
    }

    /** El perfil con sus objetivos base; sin perfil (o sin objetivos) → 422 PERFIL_REQUERIDO. */
    private PerfilDeportivo perfilRequerido(UUID usuarioId) {
        Optional<PerfilDeportivo> perfil = perfilRepositorio.findByUsuarioId(usuarioId)
                .filter(encontrado -> encontrado.getObjetivoKcal() != null);
        return perfil.orElseThrow(() -> new ReglaNegocioException("PERFIL_REQUERIDO",
                "Completa tu perfil deportivo para calcular tus objetivos"));
    }

    private static ObjetivoDiarioResponse respuesta(ObjetivoDiario objetivo) {
        Nutrientes base = new Nutrientes(objetivo.getKcalBase(), objetivo.getProteinaG(),
                objetivo.getCarbohidratosG(), objetivo.getGrasaG()).redondear();
        Nutrientes total = new Nutrientes(objetivo.getKcalTotal(), objetivo.getProteinaG(),
                objetivo.getCarbohidratosTotalG(), objetivo.getGrasaG()).redondear();
        return new ObjetivoDiarioResponse(objetivo.getFecha(), base, objetivo.getKcalEntreno(), total);
    }
}
