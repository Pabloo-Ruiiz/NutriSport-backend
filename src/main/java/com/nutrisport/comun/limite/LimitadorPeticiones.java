package com.nutrisport.comun.limite;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nutrisport.comun.excepcion.LimiteExcedidoException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Limitador de peticiones con Bucket4j (§8.1): un bucket por plan y clave (email o IP).
 * <p>
 * Los buckets viven en memoria, en una caché de Caffeine que los descarta tras 2 horas sin uso
 * (más que el periodo más largo, 1 hora). Por eso solo es válido con <b>una instancia</b> de la
 * aplicación (§5.11): con varias, cada una tendría su propio contador.
 * <p>
 * El rellenado es gradual ({@code refillGreedy}): con 5 intentos por 15 minutos se recupera un
 * intento cada 3 minutos, en lugar de los 5 de golpe al final del periodo.
 */
@Component
public class LimitadorPeticiones {

    static final String MENSAJE = "Has superado el número de intentos permitidos. Inténtalo de nuevo más tarde";

    private static final Duration EXPIRACION_SIN_USO = Duration.ofHours(2);
    /** Tope de buckets en memoria, para que no crezca sin límite con claves inventadas. */
    private static final long MAXIMO_BUCKETS = 100_000;

    private record Clave(PlanLimite plan, String valor) {
    }

    private final Cache<Clave, Bucket> buckets;
    private final TimeMeter reloj;

    public LimitadorPeticiones() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** Para los tests: permite simular el paso del tiempo. */
    LimitadorPeticiones(TimeMeter reloj) {
        this.reloj = reloj;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(EXPIRACION_SIN_USO)
                .maximumSize(MAXIMO_BUCKETS)
                .build();
    }

    /**
     * Consume un intento. Si no quedan, lanza {@link LimiteExcedidoException} (429) con los segundos
     * que faltan para el siguiente intento (cabecera {@code Retry-After}).
     */
    public void consumir(PlanLimite plan, String clave) {
        ConsumptionProbe resultado = bucket(plan, clave).tryConsumeAndReturnRemaining(1);
        if (!resultado.isConsumed()) {
            throw new LimiteExcedidoException(MENSAJE, aSegundos(resultado.getNanosToWaitForRefill()));
        }
    }

    /** Lanza {@link LimiteExcedidoException} si no queda ningún intento, sin consumir. */
    public void comprobarDisponible(PlanLimite plan, String clave) {
        Bucket bucket = buckets.getIfPresent(new Clave(plan, clave));
        if (bucket == null) {
            return;
        }
        EstimationProbe estimacion = bucket.estimateAbilityToConsume(1);
        if (!estimacion.canBeConsumed()) {
            throw new LimiteExcedidoException(MENSAJE, aSegundos(estimacion.getNanosToWaitForRefill()));
        }
    }

    /** {@code true} si no queda ningún intento. No crea el bucket si aún no existe. */
    public boolean estaAgotado(PlanLimite plan, String clave) {
        Bucket bucket = buckets.getIfPresent(new Clave(plan, clave));
        return bucket != null && bucket.getAvailableTokens() <= 0;
    }

    /** Anota un fallo (p. ej. un login con contraseña incorrecta) sin lanzar excepción. */
    public void registrarFallo(PlanLimite plan, String clave) {
        bucket(plan, clave).tryConsume(1);
    }

    /** Olvida el contador, p. ej. tras un login correcto. */
    public void reiniciar(PlanLimite plan, String clave) {
        buckets.invalidate(new Clave(plan, clave));
    }

    /** Olvida todos los contadores (tests de integración que comparten el contexto de Spring). */
    public void reiniciarTodo() {
        buckets.invalidateAll();
    }

    private Bucket bucket(PlanLimite plan, String clave) {
        return buckets.get(new Clave(plan, clave), nueva -> Bucket.builder()
                .addLimit(limite -> limite
                        .capacity(plan.getCapacidad())
                        .refillGreedy(plan.getCapacidad(), plan.getPeriodo()))
                .withCustomTimePrecision(reloj)
                .build());
    }

    /** Redondea hacia arriba y nunca devuelve 0 (Retry-After: 0 invitaría a reintentar ya). */
    private static long aSegundos(long nanos) {
        return Math.max(1, (nanos + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    }
}
