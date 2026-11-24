package com.nutrisport.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.comun.excepcion.LimiteExcedidoException;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LimitadorPeticionesTest {

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
    private final LimitadorPeticiones limitador = new LimitadorPeticiones(reloj);

    @Test
    void permiteLaCapacidadYLuegoLanza429ConLosSegundosDeEspera() {
        for (int i = 0; i < 3; i++) {
            limitador.consumir(PlanLimite.REGISTRO, "1.2.3.4");
        }

        assertThatThrownBy(() -> limitador.consumir(PlanLimite.REGISTRO, "1.2.3.4"))
                .isInstanceOfSatisfying(LimiteExcedidoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("LIMITE_EXCEDIDO");
                    // 3 por hora con rellenado gradual: un intento cada 20 minutos
                    assertThat(e.getSegundosReintento()).isEqualTo(20 * 60);
                });
    }

    @Test
    void lasClavesYLosPlanesSonIndependientes() {
        for (int i = 0; i < 3; i++) {
            limitador.consumir(PlanLimite.REGISTRO, "1.2.3.4");
        }

        assertThatCode(() -> limitador.consumir(PlanLimite.REGISTRO, "5.6.7.8")).doesNotThrowAnyException();
        assertThatCode(() -> limitador.consumir(PlanLimite.RECUPERACION, "1.2.3.4")).doesNotThrowAnyException();
    }

    @Test
    void registrarFalloNoLanzaYEstaAgotadoLoDetecta() {
        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isFalse();

        for (int i = 0; i < 7; i++) {
            limitador.registrarFallo(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com");
        }

        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isTrue();
        assertThatThrownBy(() -> limitador.comprobarDisponible(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com"))
                .isInstanceOfSatisfying(LimiteExcedidoException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(3 * 60));
    }

    @Test
    void seRecuperaUnIntentoConElTiempo() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarFallo(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com");
        }
        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isTrue();

        reloj.avanzar(Duration.ofMinutes(3));

        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isFalse();
        limitador.consumir(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com");
        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isTrue();
    }

    @Test
    void reiniciarOlvidaElContador() {
        for (int i = 0; i < 5; i++) {
            limitador.registrarFallo(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com");
        }

        limitador.reiniciar(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com");

        assertThat(limitador.estaAgotado(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com")).isFalse();
        assertThatCode(() -> limitador.comprobarDisponible(PlanLimite.LOGIN_FALLIDO, "ana@ejemplo.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void consultarNoCreaBuckets() {
        assertThat(limitador.estaAgotado(PlanLimite.REGISTRO, "nueva")).isFalse();
        assertThatCode(() -> limitador.comprobarDisponible(PlanLimite.REGISTRO, "nueva")).doesNotThrowAnyException();
    }
}
