package com.nutrisport.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.comun.config.IaProperties;
import com.nutrisport.comun.excepcion.AccesoDenegadoException;
import com.nutrisport.comun.excepcion.LimiteExcedidoException;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.servicio.LimiteIaServicio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Límites diarios de IA por usuario y verificación del email (§5.7 y §7.12). */
class LimiteIaServicioTest extends IntegracionBase {

    private final LimiteIaServicio limiteIaServicio;
    private final IaProperties propiedades;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    private UUID usuarioId;

    @Autowired
    LimiteIaServicioTest(LimiteIaServicio limiteIaServicio, IaProperties propiedades, JdbcTemplate jdbc, Clock reloj) {
        this.limiteIaServicio = limiteIaServicio;
        this.propiedades = propiedades;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        // crearUsuarioYToken() crea el usuario con el email ya verificado
        usuarioId = crearUsuarioYToken().usuario().getId();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    @Test
    void losTestsUsanLaIaSimuladaYLosLimitesConfigurados() {
        assertThat(propiedades.modo()).isEqualTo(IaProperties.Modo.SIMULADO);
        assertThat(propiedades.limiteAnalisisDiario()).isEqualTo(20);
        assertThat(propiedades.limiteMensajesDiario()).isEqualTo(50);
    }

    @Test
    void unUsuarioSinEmailVerificadoRecibeEmailNoVerificado() {
        jdbc.update("UPDATE usuario SET email_verificado_en = NULL WHERE id = ?", usuarioId);

        assertThatThrownBy(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ANALISIS_IMAGEN))
                .isInstanceOfSatisfying(AccesoDenegadoException.class,
                        error -> assertThat(error.getCodigo()).isEqualTo("EMAIL_NO_VERIFICADO"));
    }

    @Test
    void con20AnalisisHoyElSiguienteSuperaElLimiteHastaMedianoche() {
        registrarUsos(FuncionIa.ANALISIS_IMAGEN, 19, hoyAMediodia());
        assertThatCode(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ANALISIS_IMAGEN)).doesNotThrowAnyException();

        registrarUsos(FuncionIa.ANALISIS_IMAGEN, 1, hoyAMediodia());

        assertThatThrownBy(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ANALISIS_IMAGEN))
                .isInstanceOfSatisfying(LimiteExcedidoException.class, error -> {
                    assertThat(error.getCodigo()).isEqualTo("LIMITE_EXCEDIDO");
                    // Retry-After: lo que queda hasta las 00:00 de la zona de la aplicación
                    assertThat(error.getSegundosReintento()).isBetween(1L, 24L * 60 * 60);
                });
    }

    @Test
    void los20AnalisisDeAyerNoCuentanHoy() {
        registrarUsos(FuncionIa.ANALISIS_IMAGEN, 20, hoyAMediodia().minusDays(1));

        assertThatCode(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ANALISIS_IMAGEN)).doesNotThrowAnyException();
    }

    @Test
    void elAsistenteTieneSuPropioLimite() {
        registrarUsos(FuncionIa.ANALISIS_IMAGEN, 20, hoyAMediodia());
        registrarUsos(FuncionIa.ASISTENTE, 49, hoyAMediodia());

        // Los análisis agotados no afectan al asistente, que lleva su propia cuenta (50)
        assertThatCode(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ASISTENTE)).doesNotThrowAnyException();

        registrarUsos(FuncionIa.ASISTENTE, 1, hoyAMediodia());

        assertThatThrownBy(() -> limiteIaServicio.comprobar(usuarioId, FuncionIa.ASISTENTE))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Hoy a las 12:00 en la zona de la aplicación, para no depender de la hora a la que corre el test. */
    private OffsetDateTime hoyAMediodia() {
        return LocalDate.now(reloj).atTime(12, 0).atZone(reloj.getZone()).toOffsetDateTime();
    }

    /** Inserta usos con éxito en una fecha dada (UsoIaServicio siempre registraría la hora actual). */
    private void registrarUsos(FuncionIa funcion, int cantidad, OffsetDateTime cuando) {
        for (int i = 0; i < cantidad; i++) {
            jdbc.update("""
                    INSERT INTO uso_ia (id, usuario_id, funcion, proveedor, modelo, tokens_entrada, tokens_salida,
                                        latencia_ms, coste_estimado, exito, creado_en, actualizado_en)
                    VALUES (?, ?, ?, 'simulado', 'simulado', 0, 0, 10, 0, true, ?, ?)
                    """, UUID.randomUUID(), usuarioId, funcion.name(), cuando, cuando);
        }
    }
}
