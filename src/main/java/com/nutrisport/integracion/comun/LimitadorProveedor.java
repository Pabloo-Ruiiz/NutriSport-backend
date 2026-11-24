package com.nutrisport.integracion.comun;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Límites propios de las llamadas salientes a cada proveedor (docs/API_INTEGRATIONS.md §6.4), con un bucket de
 * Bucket4j por (proveedor, operación). Todas las llamadas salen del backend, es decir, de una sola IP: el
 * cupo lo comparten todos los usuarios.
 * <p>
 * {@link #intentar} nunca bloquea: si no queda cupo devuelve {@code false} y el servicio responde con los
 * datos locales y un aviso, en lugar de llamar al proveedor.
 * <p>
 * <b>Ritmo y ráfaga.</b> Cada bucket se rellena de forma continua al ritmo propio de §6.4 (p. ej. 12 por
 * minuto) y su capacidad es la ráfaga permitida. En cualquier ventana de un periodo se pueden hacer como
 * mucho {@code ritmo + ráfaga} llamadas; la ráfaga se elige para que esa suma no pase del límite oficial
 * (15/min de productos y 10/min de búsquedas en Open Food Facts, 1000/h en USDA). Un bucket con capacidad
 * igual al ritmo permitiría el doble del ritmo en un minuto y superaría el límite oficial.
 * <p>
 * Los buckets viven en memoria: el límite es correcto con una sola instancia de la aplicación (Annex B).
 */
@Component
public class LimitadorProveedor {

    /** Límites propios por proveedor y operación. */
    enum Limite {
        /** 12/min sostenido + 3 de ráfaga = 15/min, el límite oficial por IP. */
        OFF_PRODUCTO(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO, 12, 3, Duration.ofMinutes(1)),
        /** 8/min sostenido + 2 de ráfaga = 10/min, el límite oficial por IP. */
        OFF_BUSQUEDA(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.BUSQUEDA, 8, 2, Duration.ofMinutes(1)),
        /** Open Food Facts no publica límite para el resto: se aplica el más estricto (el de búsquedas). */
        OFF_CONSULTA(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.CONSULTA, 8, 2, Duration.ofMinutes(1)),
        /** Límite no publicado: margen prudente. Compartido por todas las operaciones. */
        THEMEALDB(ProveedorExterno.THEMEALDB, null, 30, 5, Duration.ofMinutes(1)),
        /** 900/h sostenido + 100 de ráfaga = 1000/h, el límite oficial por clave. Compartido por todas las operaciones. */
        USDA(ProveedorExterno.USDA, null, 900, 100, Duration.ofHours(1));

        private final ProveedorExterno proveedor;
        /** Nulo: el límite vale para todas las operaciones del proveedor. */
        private final OperacionProveedor operacion;
        private final long ritmo;
        private final long rafaga;
        private final Duration periodo;

        Limite(ProveedorExterno proveedor, OperacionProveedor operacion, long ritmo, long rafaga, Duration periodo) {
            this.proveedor = proveedor;
            this.operacion = operacion;
            this.ritmo = ritmo;
            this.rafaga = rafaga;
            this.periodo = periodo;
        }

        static Limite de(ProveedorExterno proveedor, OperacionProveedor operacion) {
            for (Limite limite : values()) {
                if (limite.proveedor == proveedor && (limite.operacion == null || limite.operacion == operacion)) {
                    return limite;
                }
            }
            throw new IllegalArgumentException("Sin límite para " + proveedor + " " + operacion);
        }

        long ritmo() {
            return ritmo;
        }

        long rafaga() {
            return rafaga;
        }

        Duration periodo() {
            return periodo;
        }
    }

    private final Map<Limite, Bucket> buckets = new EnumMap<>(Limite.class);

    public LimitadorProveedor() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** Para los tests: permite simular el paso del tiempo. */
    LimitadorProveedor(TimeMeter reloj) {
        for (Limite limite : Limite.values()) {
            buckets.put(limite, Bucket.builder()
                    .addLimit(ancho -> ancho
                            .capacity(limite.rafaga())
                            .refillGreedy(limite.ritmo(), limite.periodo()))
                    .withCustomTimePrecision(reloj)
                    .build());
        }
    }

    /**
     * Consume una llamada del cupo del proveedor y la operación. No bloquea.
     *
     * @return {@code true} si se puede llamar al proveedor; {@code false} si el cupo está agotado
     */
    public boolean intentar(ProveedorExterno proveedor, OperacionProveedor operacion) {
        return buckets.get(Limite.de(proveedor, operacion)).tryConsume(1);
    }

    /**
     * Segundos que faltan para que haya cupo de nuevo (redondeado hacia arriba, al menos 1), para la cabecera
     * {@code Retry-After} cuando {@link #intentar} devuelve {@code false}. No consume cupo.
     */
    public long segundosHastaCupo(ProveedorExterno proveedor, OperacionProveedor operacion) {
        long nanos = buckets.get(Limite.de(proveedor, operacion)).estimateAbilityToConsume(1).getNanosToWaitForRefill();
        long segundo = Duration.ofSeconds(1).toNanos();
        return Math.max(1, (nanos + segundo - 1) / segundo);
    }

    /** Vuelve a llenar todos los buckets (tests de integración que comparten el contexto de Spring). */
    public void reiniciarTodo() {
        buckets.values().forEach(bucket -> bucket.reset());
    }
}
