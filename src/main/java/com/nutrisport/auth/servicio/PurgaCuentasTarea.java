package com.nutrisport.auth.servicio;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrado físico diferido de las cuentas eliminadas (§6.3): las que llevan más de 30 días en estado
 * ELIMINADO se borran con todos sus datos (FK con ON DELETE CASCADE).
 * <p>
 * Se ejecuta cada día a las 05:00 en la zona {@code app.zona-horaria} (SchedulingConfig).
 * {@link #ejecutar(Instant)} recibe el instante actual para poder probarla.
 */
@Component
public class PurgaCuentasTarea {

    private static final Logger LOG = LoggerFactory.getLogger(PurgaCuentasTarea.class);

    static final Duration PLAZO = Duration.ofDays(30);

    private final UsuarioRepositorio usuarioRepositorio;

    public PurgaCuentasTarea(UsuarioRepositorio usuarioRepositorio) {
        this.usuarioRepositorio = usuarioRepositorio;
    }

    /** Ejecución programada: cada día a las 05:00. */
    @Scheduled(cron = "0 0 5 * * *", zone = "${app.zona-horaria}")
    public void ejecutarProgramada() {
        ejecutar(Instant.now());
    }

    /** Devuelve el número de cuentas borradas. */
    @Transactional
    public int ejecutar(Instant ahora) {
        OffsetDateTime limite = OffsetDateTime.ofInstant(ahora.minus(PLAZO), ZoneOffset.UTC);
        // TODO paso 18: borrar antes las fotos de estos usuarios en el almacenamiento
        int borradas = usuarioRepositorio.borrarEliminadosAntesDe(EstadoUsuario.ELIMINADO, limite);
        if (borradas > 0) {
            LOG.info("Purga de cuentas eliminadas: {} borradas", borradas);
        }
        return borradas;
    }
}
