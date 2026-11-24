package com.nutrisport.seguimiento.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.perfil.servicio.PerfilServicio;
import com.nutrisport.seguimiento.dto.PesoRequest;
import com.nutrisport.seguimiento.dto.PesoResponse;
import com.nutrisport.seguimiento.dto.SeriePesoResponse;
import com.nutrisport.seguimiento.dto.SeriePesoResponse.PuntoPeso;
import com.nutrisport.seguimiento.entidad.RegistroPeso;
import com.nutrisport.seguimiento.repositorio.RegistroPesoRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evolución del peso del usuario autenticado (§3.F y §7.7). El pesaje más reciente es el peso actual
 * del perfil: al registrarlo o borrarlo se sincroniza el perfil, lo que recalcula sus objetivos (si no
 * son manuales) y los objetivos diarios de hoy y de los días futuros.
 */
@Service
@Transactional
public class PesoServicio {

    static final int MAXIMO_DIAS_SERIE = 366;
    static final int DIAS_MEDIA_MOVIL = 7;

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    private final RegistroPesoRepositorio pesoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final PerfilServicio perfilServicio;
    private final Clock reloj;

    public PesoServicio(RegistroPesoRepositorio pesoRepositorio, UsuarioRepositorio usuarioRepositorio,
            SeguridadUtils seguridadUtils, PerfilServicio perfilServicio, Clock reloj) {
        this.pesoRepositorio = pesoRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.perfilServicio = perfilServicio;
        this.reloj = reloj;
    }

    /**
     * Registra el pesaje del día o sustituye el que ya hubiera (upsert por fecha). Si es el más reciente,
     * pasa a ser el peso actual del perfil.
     */
    public PesoResponse registrar(PesoRequest peticion) {
        if (peticion.fecha().isAfter(LocalDate.now(reloj))) {
            throw invalida("fecha", "No puede ser futura");
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
        RegistroPeso registro = pesoRepositorio.findByUsuarioIdAndFecha(usuarioId, peticion.fecha())
                .orElseGet(() -> {
                    RegistroPeso nuevo = new RegistroPeso();
                    nuevo.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
                    nuevo.setFecha(peticion.fecha());
                    return nuevo;
                });
        registro.setPesoKg(peticion.pesoKg());
        pesoRepositorio.saveAndFlush(registro);
        if (esElMasReciente(usuarioId, registro)) {
            perfilServicio.actualizarPesoActual(usuarioId, registro.getPesoKg());
        }
        return respuesta(registro);
    }

    /** Borra un pesaje propio; si era el más reciente, el perfil toma el anterior. Ajeno → 404. */
    public void eliminar(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        RegistroPeso registro = pesoRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pesaje no existe"));
        boolean eraElMasReciente = esElMasReciente(usuarioId, registro);
        pesoRepositorio.delete(registro);
        pesoRepositorio.flush();
        if (eraElMasReciente) {
            // Si ya no queda ningún pesaje, el perfil conserva su peso
            pesoRepositorio.findFirstByUsuarioIdOrderByFechaDesc(usuarioId)
                    .ifPresent(anterior -> perfilServicio.actualizarPesoActual(usuarioId, anterior.getPesoKg()));
        }
    }

    /**
     * Pesajes entre dos fechas (ambas incluidas, máximo 366 días) con su media móvil de 7 días. La media
     * de los primeros puntos tiene en cuenta los pesajes de los 6 días anteriores a {@code desde}.
     */
    @Transactional(readOnly = true)
    public SeriePesoResponse serie(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw invalida("hasta", "Debe ser igual o posterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_SERIE) {
            throw invalida("hasta", "El rango no puede superar los " + MAXIMO_DIAS_SERIE + " días");
        }
        List<RegistroPeso> pesajes = pesoRepositorio.findByUsuarioIdAndFechaBetweenOrderByFechaAsc(
                seguridadUtils.usuarioActualId(), desde.minusDays(DIAS_MEDIA_MOVIL - 1L), hasta);

        List<PuntoPeso> puntos = new ArrayList<>();
        for (RegistroPeso pesaje : pesajes) {
            if (!pesaje.getFecha().isBefore(desde)) {
                puntos.add(new PuntoPeso(pesaje.getFecha(), pesaje.getPesoKg(), mediaMovil(pesajes, pesaje.getFecha())));
            }
        }
        BigDecimal variacion = puntos.size() < 2 ? null
                : puntos.get(puntos.size() - 1).pesoKg().subtract(puntos.get(0).pesoKg());
        return new SeriePesoResponse(puntos, variacion);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private boolean esElMasReciente(UUID usuarioId, RegistroPeso registro) {
        return pesoRepositorio.findFirstByUsuarioIdOrderByFechaDesc(usuarioId)
                .map(ultimo -> ultimo.getId().equals(registro.getId()))
                .orElse(false);
    }

    /** Media de los pesajes de los 7 días naturales que terminan en {@code fecha}, con dos decimales. */
    private static BigDecimal mediaMovil(List<RegistroPeso> pesajes, LocalDate fecha) {
        LocalDate inicio = fecha.minusDays(DIAS_MEDIA_MOVIL - 1L);
        List<BigDecimal> ventana = pesajes.stream()
                .filter(pesaje -> !pesaje.getFecha().isBefore(inicio) && !pesaje.getFecha().isAfter(fecha))
                .map(RegistroPeso::getPesoKg)
                .toList();
        return ventana.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(ventana.size()), 2, RoundingMode.HALF_UP);
    }

    private static PesoResponse respuesta(RegistroPeso registro) {
        return new PesoResponse(registro.getId(), registro.getFecha(), registro.getPesoKg());
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
