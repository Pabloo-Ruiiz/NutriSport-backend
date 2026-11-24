package com.nutrisport.integracion.comun;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Disponibilidad de los proveedores externos tras un fallo (docs/API_INTEGRATIONS.md §6.3). */
class EstadoProveedorTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-24T12:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final EstadoProveedor estado = new EstadoProveedor(reloj);

    @Test
    void sinFallosTodosEstanDisponibles() {
        for (ProveedorExterno proveedor : ProveedorExterno.values()) {
            assertThat(estado.estaDisponible(proveedor)).isTrue();
        }
    }

    @Test
    void caidoHastaQuePasaElPlazoYSoloEseProveedor() {
        estado.marcarCaido(ProveedorExterno.OPEN_FOOD_FACTS, Duration.ofSeconds(60));

        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isFalse();
        assertThat(estado.estaDisponible(ProveedorExterno.USDA)).isTrue();

        reloj.avanzar(Duration.ofSeconds(59));
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isFalse();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isTrue();
    }

    @Test
    void unFalloConMenosEsperaNoAcortaUnPlazoMasLargo() {
        // Un Retry-After de 10 minutos seguido de un timeout normal de 60 segundos
        estado.marcarCaido(ProveedorExterno.THEMEALDB, Duration.ofMinutes(10));
        estado.marcarCaido(ProveedorExterno.THEMEALDB, Duration.ofSeconds(60));

        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(estado.estaDisponible(ProveedorExterno.THEMEALDB)).isFalse();

        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(estado.estaDisponible(ProveedorExterno.THEMEALDB)).isTrue();
    }

    @Test
    void unaDuracionNulaOCeroNoMarcaNada() {
        estado.marcarCaido(ProveedorExterno.USDA, Duration.ZERO);
        estado.marcarCaido(ProveedorExterno.USDA, null);

        assertThat(estado.estaDisponible(ProveedorExterno.USDA)).isTrue();
    }

    @Test
    void reiniciarTodoOlvidaLosFallos() {
        estado.marcarCaido(ProveedorExterno.OPEN_FOOD_FACTS, Duration.ofMinutes(1));

        estado.reiniciarTodo();

        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isTrue();
    }
}
