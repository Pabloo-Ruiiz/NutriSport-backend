package com.nutrisport.comidas.servicio;

import com.nutrisport.almacenamiento.AlmacenamientoProperties;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Elimina las fotos de análisis que han superado su periodo de retención (§5.8). */
@Component
public class LimpiezaImagenesTarea {

    static final int TAMANO_LOTE = 200;
    static final int DIAS_RETENCION_TERMINALES = 7;

    private static final Logger LOG = LoggerFactory.getLogger(LimpiezaImagenesTarea.class);
    private static final List<EstadoAnalisis> ESTADOS_TERMINALES =
            List.of(EstadoAnalisis.DESCARTADO, EstadoAnalisis.FALLIDO);

    private final AnalisisImagenRepositorio analisisRepositorio;
    private final AlmacenamientoServicio almacenamiento;
    private final AlmacenamientoProperties propiedades;
    private final Clock reloj;

    public LimpiezaImagenesTarea(AnalisisImagenRepositorio analisisRepositorio,
            AlmacenamientoServicio almacenamiento, AlmacenamientoProperties propiedades, Clock reloj) {
        this.analisisRepositorio = analisisRepositorio;
        this.almacenamiento = almacenamiento;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** Ejecución programada: cada día a las 04:00 en la zona horaria de la aplicación. */
    @Scheduled(cron = "0 0 4 * * *", zone = "${app.zona-horaria}")
    @Transactional
    public void ejecutarProgramada() {
        ejecutar(LocalDate.now(reloj));
    }

    /** Borra las claves vencidas y devuelve cuántos objetos ha eliminado. */
    @Transactional
    public int ejecutar(LocalDate hoy) {
        OffsetDateTime limiteRetencion = hoy.minusDays(propiedades.retencionDias())
                .atStartOfDay(reloj.getZone()).toOffsetDateTime();
        OffsetDateTime limiteTerminal = hoy.minusDays(DIAS_RETENCION_TERMINALES)
                .atStartOfDay(reloj.getZone()).toOffsetDateTime();
        int objetosEliminados = 0;
        int analisisProcesados = 0;

        while (true) {
            List<AnalisisImagen> lote = analisisRepositorio.buscarParaLimpieza(limiteRetencion, limiteTerminal,
                    ESTADOS_TERMINALES, PageRequest.of(0, TAMANO_LOTE));
            if (lote.isEmpty()) {
                break;
            }

            for (AnalisisImagen analisis : lote) {
                objetosEliminados += eliminarSiExiste(analisis.getImagenKey());
                objetosEliminados += eliminarSiExiste(analisis.getMiniaturaKey());
                analisis.setImagenKey(null);
                analisis.setMiniaturaKey(null);
            }
            analisisRepositorio.flush();
            analisisProcesados += lote.size();
        }

        LOG.info("Limpieza de imágenes completada: {} objetos eliminados en {} análisis",
                objetosEliminados, analisisProcesados);
        return objetosEliminados;
    }

    private int eliminarSiExiste(String clave) {
        if (clave == null) {
            return 0;
        }
        almacenamiento.eliminar(clave);
        return 1;
    }
}
