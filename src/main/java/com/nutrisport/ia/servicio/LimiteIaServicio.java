package com.nutrisport.ia.servicio;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.comun.config.IaProperties;
import com.nutrisport.comun.excepcion.AccesoDenegadoException;
import com.nutrisport.comun.excepcion.LimiteExcedidoException;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.repositorio.UsoIaRepositorio;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Límites de uso de la IA por usuario (§5.7 y §7.12), comprobados antes de cada llamada:
 * <ul>
 * <li>Email sin verificar → 403 EMAIL_NO_VERIFICADO.</li>
 * <li>Límite diario alcanzado → 429 LIMITE_EXCEDIDO con {@code Retry-After} hasta la medianoche de
 * {@code app.zona-horaria}. ANALISIS_IMAGEN y ETIQUETA comparten {@code limite-analisis-diario};
 * ASISTENTE usa {@code limite-mensajes-diario}; TEXTO no tiene límite por usuario.</li>
 * </ul>
 * Solo cuentan las llamadas con éxito del día en curso.
 */
@Service
@Transactional(readOnly = true)
public class LimiteIaServicio {

    private static final Set<FuncionIa> ANALISIS = Set.of(FuncionIa.ANALISIS_IMAGEN, FuncionIa.ETIQUETA);
    private static final Set<FuncionIa> MENSAJES = Set.of(FuncionIa.ASISTENTE);

    private final UsuarioRepositorio usuarioRepositorio;
    private final UsoIaRepositorio usoIaRepositorio;
    private final IaProperties propiedades;
    private final Clock reloj;

    public LimiteIaServicio(UsuarioRepositorio usuarioRepositorio, UsoIaRepositorio usoIaRepositorio,
            IaProperties propiedades, Clock reloj) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.usoIaRepositorio = usoIaRepositorio;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public void comprobar(UUID usuarioId, FuncionIa funcion) {
        Usuario usuario = usuarioRepositorio.findById(usuarioId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("El usuario no existe"));
        if (!usuario.emailVerificado()) {
            throw new AccesoDenegadoException("EMAIL_NO_VERIFICADO",
                    "Verifica tu email para usar las funciones de inteligencia artificial");
        }
        if (funcion == FuncionIa.TEXTO) {
            return;
        }
        boolean esAnalisis = ANALISIS.contains(funcion);
        int limite = esAnalisis ? propiedades.limiteAnalisisDiario() : propiedades.limiteMensajesDiario();
        ZonedDateTime ahora = ZonedDateTime.now(reloj);
        ZonedDateTime inicioDia = ahora.toLocalDate().atStartOfDay(reloj.getZone());
        long usados = usoIaRepositorio.contarExitosas(usuarioId, esAnalisis ? ANALISIS : MENSAJES,
                inicioDia.toOffsetDateTime());
        if (usados >= limite) {
            throw new LimiteExcedidoException(esAnalisis
                    ? "Has alcanzado el límite de " + limite + " análisis por hoy. Mañana podrás hacer más"
                    : "Has alcanzado el límite de " + limite + " mensajes al asistente por hoy",
                    segundosHastaMedianoche(ahora));
        }
    }

    /** Segundos hasta las 00:00 del día siguiente en la zona de la aplicación (al menos 1). */
    private long segundosHastaMedianoche(ZonedDateTime ahora) {
        LocalDate manana = ahora.toLocalDate().plusDays(1);
        OffsetDateTime medianoche = manana.atStartOfDay(ahora.getZone()).toOffsetDateTime();
        return Math.max(1, Duration.between(ahora.toOffsetDateTime(), medianoche).toSeconds());
    }
}
