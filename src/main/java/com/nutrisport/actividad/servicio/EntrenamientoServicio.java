package com.nutrisport.actividad.servicio;

import com.nutrisport.actividad.dto.EntrenamientoRequest;
import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.dto.TipoActividadResponse;
import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.TipoActividad;
import com.nutrisport.actividad.mapper.EntrenamientoMapper;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.actividad.repositorio.TipoActividadRepositorio;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrenamientos del usuario autenticado (§3.G y §7.8). El gasto neto se calcula con el MET de la
 * actividad y el peso actual del perfil, por eso registrar entrenamientos exige perfil (422
 * PERFIL_REQUERIDO). Después de cada cambio se recalcula el objetivo del día afectado (y el del día
 * anterior si el entrenamiento cambia de fecha).
 */
@Service
@Transactional
public class EntrenamientoServicio {

    static final int MAXIMO_DIAS_HISTORIAL = 92;

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADO = "El entrenamiento no existe";

    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final TipoActividadRepositorio tipoActividadRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final CalculadoraGasto calculadoraGasto;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final EntrenamientoMapper entrenamientoMapper;
    private final Clock reloj;

    public EntrenamientoServicio(EntrenamientoRepositorio entrenamientoRepositorio,
            TipoActividadRepositorio tipoActividadRepositorio, PerfilDeportivoRepositorio perfilRepositorio,
            UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils, CalculadoraGasto calculadoraGasto,
            ObjetivoDiarioServicio objetivoDiarioServicio, EntrenamientoMapper entrenamientoMapper, Clock reloj) {
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.tipoActividadRepositorio = tipoActividadRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.calculadoraGasto = calculadoraGasto;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.entrenamientoMapper = entrenamientoMapper;
        this.reloj = reloj;
    }

    /** Catálogo de actividades activas con su MET. */
    @Transactional(readOnly = true)
    public List<TipoActividadResponse> tipos() {
        return tipoActividadRepositorio.findByActivoTrueOrderByCategoriaAscNombreAsc().stream()
                .map(entrenamientoMapper::aRespuesta)
                .toList();
    }

    /**
     * Registra o planifica un entrenamiento. Sin estado: PLANIFICADO si la fecha es futura, REALIZADO si
     * no. No se admite REALIZADO con fecha futura.
     */
    public EntrenamientoResponse crear(EntrenamientoRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        PerfilDeportivo perfil = perfilRequerido(usuarioId);
        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        EstadoEntrenamiento estado = peticion.estado() != null ? peticion.estado() : estadoPorDefecto(peticion.fechaHora());
        aplicar(peticion, estado, entrenamiento, perfil, null);
        entrenamientoRepositorio.save(entrenamiento);
        objetivoDiarioServicio.recalcular(usuarioId, fechaLocal(entrenamiento.getFechaHora()));
        return entrenamientoMapper.aRespuesta(entrenamiento);
    }

    /**
     * Sustituye los datos del entrenamiento (incluido marcarlo como realizado). Sin estado se conserva el
     * que tenía. El gasto se recalcula con el peso actual del perfil.
     */
    public EntrenamientoResponse editar(UUID id, EntrenamientoRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        // Primero la propiedad: un entrenamiento ajeno es 404 aunque el usuario no tenga perfil
        Entrenamiento entrenamiento = propio(id, usuarioId);
        PerfilDeportivo perfil = perfilRequerido(usuarioId);
        LocalDate fechaAnterior = fechaLocal(entrenamiento.getFechaHora());
        String tipoActual = entrenamiento.getTipoActividad().getCodigo();
        EstadoEntrenamiento estado = peticion.estado() != null ? peticion.estado() : entrenamiento.getEstado();
        aplicar(peticion, estado, entrenamiento, perfil, tipoActual);
        recalcularObjetivos(usuarioId, fechaAnterior, fechaLocal(entrenamiento.getFechaHora()));
        return entrenamientoMapper.aRespuesta(entrenamiento);
    }

    /** Marca el entrenamiento como realizado; no se admite si su fecha es futura. */
    public EntrenamientoResponse marcarRealizado(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Entrenamiento entrenamiento = propio(id, usuarioId);
        validarEstado(EstadoEntrenamiento.REALIZADO, entrenamiento.getFechaHora());
        entrenamiento.setEstado(EstadoEntrenamiento.REALIZADO);
        objetivoDiarioServicio.recalcular(usuarioId, fechaLocal(entrenamiento.getFechaHora()));
        return entrenamientoMapper.aRespuesta(entrenamiento);
    }

    public void eliminar(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Entrenamiento entrenamiento = propio(id, usuarioId);
        LocalDate fecha = fechaLocal(entrenamiento.getFechaHora());
        entrenamientoRepositorio.delete(entrenamiento);
        entrenamientoRepositorio.flush();
        objetivoDiarioServicio.recalcular(usuarioId, fecha);
    }

    /**
     * Entrenamientos entre dos fechas (ambas incluidas, máximo 92 días) en orden cronológico,
     * opcionalmente de un estado.
     */
    @Transactional(readOnly = true)
    public List<EntrenamientoResponse> listar(LocalDate desde, LocalDate hasta, EstadoEntrenamiento estado) {
        if (hasta.isBefore(desde)) {
            throw invalida("hasta", "Debe ser igual o posterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_HISTORIAL) {
            throw invalida("hasta", "El rango no puede superar los " + MAXIMO_DIAS_HISTORIAL + " días");
        }
        ZoneId zona = reloj.getZone();
        return entrenamientoRepositorio
                .buscarEntre(seguridadUtils.usuarioActualId(), desde.atStartOfDay(zona).toOffsetDateTime(),
                        hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime())
                .stream()
                .filter(entrenamiento -> estado == null || entrenamiento.getEstado() == estado)
                .map(entrenamientoMapper::aRespuesta)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /**
     * Copia la petición al entrenamiento y calcula su gasto. {@code tipoActual} es el tipo que ya tenía
     * (al editar se admite aunque se haya desactivado).
     */
    private void aplicar(EntrenamientoRequest peticion, EstadoEntrenamiento estado, Entrenamiento entrenamiento,
            PerfilDeportivo perfil, String tipoActual) {
        validarEstado(estado, peticion.fechaHora());
        TipoActividad tipo = tipoActividadRepositorio.findById(peticion.tipoActividadCodigo())
                .filter(encontrado -> encontrado.isActivo() || encontrado.getCodigo().equals(tipoActual))
                .orElseThrow(() -> invalida("tipoActividadCodigo", "No es un tipo de actividad del catálogo"));
        entrenamiento.setTipoActividad(tipo);
        entrenamiento.setFechaHora(peticion.fechaHora());
        entrenamiento.setDuracionMin(peticion.duracionMin().shortValue());
        entrenamiento.setIntensidad(peticion.intensidad());
        entrenamiento.setRpe(peticion.rpe() == null ? null : peticion.rpe().shortValue());
        entrenamiento.setEstado(estado);
        entrenamiento.setDistanciaKm(peticion.distanciaKm());
        entrenamiento.setNotas(peticion.notas() == null || peticion.notas().isBlank() ? null : peticion.notas().strip());
        entrenamiento.setKcalEstimadas(calculadoraGasto.kcalNetas(tipo.getMet(), perfil.getPesoActualKg(),
                peticion.duracionMin(), peticion.intensidad()));
    }

    private EstadoEntrenamiento estadoPorDefecto(OffsetDateTime fechaHora) {
        return fechaHora.isAfter(OffsetDateTime.now(reloj)) ? EstadoEntrenamiento.PLANIFICADO
                : EstadoEntrenamiento.REALIZADO;
    }

    /** Un entrenamiento futuro todavía no se ha podido realizar. */
    private void validarEstado(EstadoEntrenamiento estado, OffsetDateTime fechaHora) {
        if (estado == EstadoEntrenamiento.REALIZADO && fechaHora.isAfter(OffsetDateTime.now(reloj))) {
            throw invalida("estado", "Un entrenamiento con fecha futura no puede estar realizado");
        }
    }

    private void recalcularObjetivos(UUID usuarioId, LocalDate fechaAnterior, LocalDate fechaNueva) {
        entrenamientoRepositorio.flush();
        objetivoDiarioServicio.recalcular(usuarioId, fechaNueva);
        if (!fechaAnterior.equals(fechaNueva)) {
            objetivoDiarioServicio.recalcular(usuarioId, fechaAnterior);
        }
    }

    private Entrenamiento propio(UUID id, UUID usuarioId) {
        return entrenamientoRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    private PerfilDeportivo perfilRequerido(UUID usuarioId) {
        return perfilRepositorio.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("PERFIL_REQUERIDO",
                        "Completa tu perfil deportivo para registrar entrenamientos"));
    }

    private LocalDate fechaLocal(OffsetDateTime fechaHora) {
        return fechaHora.atZoneSameInstant(reloj.getZone()).toLocalDate();
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
