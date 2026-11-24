package com.nutrisport.integracion.comun;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Límites propios de las llamadas a los proveedores externos (docs/API_INTEGRATIONS.md §6.4). */
class LimitadorProveedorTest {

    /** Reloj manual para simular el paso del tiempo. */
    private static final class RelojManual implements TimeMeter {
        private long nanos;

        void avanzar(Duration duracion) {
            nanos += duracion.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final LimitadorProveedor limitador = new LimitadorProveedor(reloj);

    /**
     * Llamando sin parar durante todo un periodo (un intento cada décima de segundo o cada segundo) nunca se
     * supera el límite oficial del proveedor, y a la larga se alcanza el ritmo propio.
     */
    @ParameterizedTest(name = "{0} {1}: como mucho {2} por periodo")
    @CsvSource({
        "OPEN_FOOD_FACTS, PRODUCTO, 15, PT1M, PT0.1S",
        "OPEN_FOOD_FACTS, BUSQUEDA, 10, PT1M, PT0.1S",
        "THEMEALDB,       CONSULTA, 35, PT1M, PT0.1S",
        "USDA,            BUSQUEDA, 1000, PT1H, PT1S"
    })
    void nuncaSuperaElLimiteOficialEnUnPeriodo(ProveedorExterno proveedor, OperacionProveedor operacion,
            int maximo, Duration periodo, Duration paso) {
        int concedidas = 0;
        for (long t = 0; t < periodo.toNanos(); t += paso.toNanos()) {
            if (limitador.intentar(proveedor, operacion)) {
                concedidas++;
            }
            reloj.avanzar(paso);
        }

        assertThat(concedidas).isLessThanOrEqualTo(maximo).isGreaterThanOrEqualTo(maximo - 1);
    }

    @Test
    void conElCupoAgotadoDevuelveFalseSinBloquearYSeRecuperaConElTiempo() {
        // Open Food Facts, producto: 3 de ráfaga y una más cada 5 segundos (12/min)
        for (int i = 0; i < 3; i++) {
            assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO)).isTrue();
        }
        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO)).isFalse();

        reloj.avanzar(Duration.ofSeconds(4));
        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO)).isFalse();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO)).isTrue();
    }

    @Test
    void productosYBusquedasDeOpenFoodFactsTienenCuposSeparados() {
        while (limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.PRODUCTO)) {
            // agota los productos
        }

        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.BUSQUEDA)).isTrue();
        assertThat(limitador.intentar(ProveedorExterno.USDA, OperacionProveedor.PRODUCTO)).isTrue();
    }

    @Test
    void theMealDbYUsdaCompartenUnCupoParaTodasSusOperaciones() {
        int concedidas = 0;
        for (OperacionProveedor operacion : OperacionProveedor.values()) {
            for (int i = 0; i < 5; i++) {
                if (limitador.intentar(ProveedorExterno.THEMEALDB, operacion)) {
                    concedidas++;
                }
            }
        }

        // 15 intentos repartidos entre las tres operaciones, pero un solo cupo de 5 de ráfaga
        assertThat(concedidas).isEqualTo(5);
    }

    @Test
    void reiniciarTodoVuelveALlenarLosCupos() {
        while (limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.BUSQUEDA)) {
            // agota las búsquedas
        }

        limitador.reiniciarTodo();

        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.BUSQUEDA)).isTrue();
    }
}
