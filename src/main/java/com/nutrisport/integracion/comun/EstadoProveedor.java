package com.nutrisport.integracion.comun;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Disponibilidad de cada proveedor externo (docs/API_INTEGRATIONS.md §6.3). Tras un fallo (timeout o 5xx
 * después del reintento, o un 429/503 del proveedor) se marca como caído durante un tiempo, y mientras tanto
 * no se le llama: los servicios responden con los datos locales.
 * <p>
 * Vive en memoria, como los límites: vale para una sola instancia de la aplicación.
 */
@Component
public class EstadoProveedor {

    private final Map<ProveedorExterno, Instant> noDisponibleHasta = new ConcurrentHashMap<>();
    private final Clock reloj;

    public EstadoProveedor(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Marca el proveedor como no disponible durante {@code duracion} desde ahora. Si ya estaba marcado hasta
     * más tarde, se conserva la fecha más lejana (p. ej. un {@code Retry-After} largo no lo acorta un fallo
     * posterior con una espera menor).
     */
    public void marcarCaido(ProveedorExterno proveedor, Duration duracion) {
        if (duracion == null || duracion.isNegative() || duracion.isZero()) {
            return;
        }
        Instant hasta = reloj.instant().plus(duracion);
        noDisponibleHasta.merge(proveedor, hasta, (actual, nuevo) -> nuevo.isAfter(actual) ? nuevo : actual);
    }

    /** {@code true} si no está marcado como caído o si el plazo ya ha pasado. */
    public boolean estaDisponible(ProveedorExterno proveedor) {
        Instant hasta = noDisponibleHasta.get(proveedor);
        return hasta == null || !reloj.instant().isBefore(hasta);
    }

    /** Segundos que faltan para volver a llamar al proveedor (redondeado hacia arriba); 0 si está disponible. */
    public long segundosHastaDisponible(ProveedorExterno proveedor) {
        Instant hasta = noDisponibleHasta.get(proveedor);
        if (hasta == null) {
            return 0;
        }
        long milisegundos = Duration.between(reloj.instant(), hasta).toMillis();
        return milisegundos <= 0 ? 0 : (milisegundos + 999) / 1000;
    }

    /** Olvida todos los fallos (tests de integración que comparten el contexto de Spring). */
    public void reiniciarTodo() {
        noDisponibleHasta.clear();
    }
}
