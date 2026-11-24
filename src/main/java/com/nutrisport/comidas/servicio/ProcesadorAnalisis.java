package com.nutrisport.comidas.servicio;

import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import com.nutrisport.comun.config.AsyncConfig;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.AlimentoDetectado;
import com.nutrisport.ia.puerto.ContextoReconocimiento;
import com.nutrisport.ia.puerto.ReconocimientoAlimentosPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.UsoIaServicio;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa en segundo plano los análisis recién creados (§4.3, pasos 2 a 5): PROCESANDO → foto a la IA →
 * emparejamiento con el catálogo → COMPLETADO, o FALLIDO con un mensaje genérico (el detalle, solo en el
 * log). Se ejecuta tras el commit de la creación, en el ejecutor "ejecutorIa". Cada cambio de estado va
 * en su propia transacción, para que el frontend vea PROCESANDO mientras se espera a la IA, y la llamada
 * a la IA queda fuera de toda transacción. Siempre registra el uso de la IA.
 */
@Component
public class ProcesadorAnalisis {

    static final String SIN_ALIMENTOS = "No hemos detectado alimentos en la foto. Puedes registrar la comida manualmente";
    static final String ERROR_GENERICO = "No se ha podido analizar la foto. Puedes registrar la comida manualmente";

    private static final Logger LOG = LoggerFactory.getLogger(ProcesadorAnalisis.class);

    private final AnalisisImagenRepositorio analisisRepositorio;
    private final AlmacenamientoServicio almacenamiento;
    private final ReconocimientoAlimentosPuerto reconocimiento;
    private final EmparejadorCatalogo emparejador;
    private final UsoIaServicio usoIaServicio;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public ProcesadorAnalisis(AnalisisImagenRepositorio analisisRepositorio, AlmacenamientoServicio almacenamiento,
            ReconocimientoAlimentosPuerto reconocimiento, EmparejadorCatalogo emparejador, UsoIaServicio usoIaServicio,
            PlatformTransactionManager gestorTransacciones, Clock reloj) {
        this.analisisRepositorio = analisisRepositorio;
        this.almacenamiento = almacenamiento;
        this.reconocimiento = reconocimiento;
        this.emparejador = emparejador;
        this.usoIaServicio = usoIaServicio;
        // REQUIRES_NEW: tras el commit (y con el ejecutor síncrono de los tests, en el mismo hilo) la
        // transacción que creó el análisis aún está ligada al hilo; unirse a ella perdería los cambios
        this.transacciones = new TransactionTemplate(gestorTransacciones);
        this.transacciones.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reloj = reloj;
    }

    /** Datos del análisis que hacen falta fuera de la transacción. */
    private record Pendiente(UUID usuarioId, String imagenKey, TipoComida tipoComida, String descripcion) {
    }

    @Async(AsyncConfig.EJECUTOR_IA)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void procesar(AnalisisCreadoEvento evento) {
        UUID analisisId = evento.analisisId();
        Pendiente pendiente = transacciones.execute(estado -> marcarProcesando(analisisId));
        if (pendiente == null) {
            return;
        }
        long inicio = System.nanoTime();
        ResultadoIa<List<AlimentoDetectado>> resultado = null;
        try {
            byte[] jpeg = almacenamiento.descargar(pendiente.imagenKey());
            resultado = reconocimiento.reconocer(jpeg,
                    new ContextoReconocimiento(pendiente.tipoComida(), pendiente.descripcion()));
            List<AlimentoDetectado> detectados = resultado.contenido();
            List<CandidatoAnalisis> candidatos = detectados.stream()
                    .flatMap(detectado -> emparejador.emparejar(detectado, pendiente.usuarioId()).stream())
                    .toList();
            long latenciaMs = milisegundosDesde(inicio);
            ResultadoIa<List<AlimentoDetectado>> respuesta = resultado;
            transacciones.executeWithoutResult(estado -> completar(analisisId, respuesta, candidatos, latenciaMs));
            usoIaServicio.registrar(pendiente.usuarioId(), FuncionIa.ANALISIS_IMAGEN, resultado, latenciaMs, true);
        } catch (RuntimeException e) {
            long latenciaMs = milisegundosDesde(inicio);
            LOG.error("Ha fallado el análisis {}", analisisId, e);
            transacciones.executeWithoutResult(estado -> fallar(analisisId, ERROR_GENERICO, latenciaMs));
            usoIaServicio.registrar(pendiente.usuarioId(), FuncionIa.ANALISIS_IMAGEN, resultado, latenciaMs, false);
        }
    }

    /** PENDIENTE → PROCESANDO; nulo si ya no está pendiente (p. ej. lo marcó fallido la tarea de huérfanos). */
    private Pendiente marcarProcesando(UUID analisisId) {
        AnalisisImagen analisis = analisisRepositorio.findById(analisisId).orElse(null);
        if (analisis == null || analisis.getEstado() != EstadoAnalisis.PENDIENTE) {
            return null;
        }
        analisis.setEstado(EstadoAnalisis.PROCESANDO);
        return new Pendiente(analisis.getUsuario().getId(), analisis.getImagenKey(), analisis.getTipoComida(),
                analisis.getDescripcionUsuario());
    }

    /** PROCESANDO → COMPLETADO, o FALLIDO si la IA no ve comida. Si ya no está PROCESANDO, no lo toca. */
    private void completar(UUID analisisId, ResultadoIa<List<AlimentoDetectado>> resultado,
            List<CandidatoAnalisis> candidatos, long latenciaMs) {
        AnalisisImagen analisis = analisisRepositorio.findById(analisisId).orElse(null);
        if (analisis == null || analisis.getEstado() != EstadoAnalisis.PROCESANDO) {
            return;
        }
        analisis.setResultadoBruto(resultado.contenido());
        analisis.setCandidatos(candidatos);
        analisis.setProveedor(usoIaServicio.proveedorActual());
        analisis.setModelo(resultado.modelo());
        analisis.setVersionPrompt(ReconocimientoAlimentosPuerto.VERSION_PROMPT);
        analisis.setLatenciaMs((int) latenciaMs);
        analisis.setCosteEstimado(usoIaServicio.costeEstimado(resultado.tokensEntrada(), resultado.tokensSalida()));
        analisis.setCompletadoEn(OffsetDateTime.now(reloj));
        if (resultado.contenido().isEmpty()) {
            analisis.setEstado(EstadoAnalisis.FALLIDO);
            analisis.setErrorMensaje(SIN_ALIMENTOS);
        } else {
            analisis.setEstado(EstadoAnalisis.COMPLETADO);
        }
    }

    private void fallar(UUID analisisId, String mensaje, long latenciaMs) {
        analisisRepositorio.findById(analisisId)
                .filter(analisis -> analisis.getEstado() == EstadoAnalisis.PROCESANDO)
                .ifPresent(analisis -> {
                    analisis.setEstado(EstadoAnalisis.FALLIDO);
                    analisis.setErrorMensaje(mensaje);
                    analisis.setLatenciaMs((int) latenciaMs);
                    analisis.setProveedor(usoIaServicio.proveedorActual());
                    analisis.setVersionPrompt(ReconocimientoAlimentosPuerto.VERSION_PROMPT);
                    analisis.setCompletadoEn(OffsetDateTime.now(reloj));
                });
    }

    private static long milisegundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
