package com.nutrisport.asistente;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.asistente.servicio.FiltroTemasSensibles;
import com.nutrisport.asistente.servicio.RespuestaSegura;
import com.nutrisport.asistente.servicio.RespuestaSegura.Motivo;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Filtro previo del asistente (§4.4): los temas sensibles reciben una respuesta segura sin llamar al modelo, y las
 * preguntas normales (también las que mencionan suplementos o cifras) pasan.
 */
class FiltroTemasSensiblesTest {

    private final FiltroTemasSensibles filtro = new FiltroTemasSensibles();

    static Stream<Arguments> sensibles() {
        return Stream.of(
                Arguments.of("quiero una dieta de 500 kcal para perder 5 kilos en una semana",
                        Motivo.CONDUCTA_ALIMENTARIA_RIESGO),
                Arguments.of("¿cuántos mg de ibuprofeno tomo después de correr?", Motivo.DOSIS_MEDICAMENTOS),
                Arguments.of("Quiero comer 800 calorías al día", Motivo.CONDUCTA_ALIMENTARIA_RIESGO),
                Arguments.of("¿Cómo pierdo... quiero perder 3 kg en 10 días", Motivo.CONDUCTA_ALIMENTARIA_RIESGO),
                Arguments.of("Después de cenar me provoco el vómito", Motivo.CONDUCTA_ALIMENTARIA_RIESGO),
                Arguments.of("¿Puedo tomar laxantes para adelgazar?", Motivo.CONDUCTA_ALIMENTARIA_RIESGO),
                Arguments.of("¿Cuánta creatina debo tomar al día?", Motivo.DOSIS_MEDICAMENTOS),
                Arguments.of("¿Qué dosis de cafeína antes de una carrera?", Motivo.DOSIS_MEDICAMENTOS),
                Arguments.of("¿Dónde consigo anabolizantes?", Motivo.DOSIS_MEDICAMENTOS),
                Arguments.of("Estoy embarazada, ¿puedo seguir el plan?", Motivo.CONSULTA_MEDICA),
                Arguments.of("Tengo dolor en el pecho cuando corro", Motivo.CONSULTA_MEDICA),
                Arguments.of("Me han diagnosticado diabetes, ¿qué como?", Motivo.CONSULTA_MEDICA),
                Arguments.of("Tengo una lesión en el tobillo", Motivo.CONSULTA_MEDICA));
    }

    @ParameterizedTest
    @MethodSource("sensibles")
    void losTemasSensiblesRecibenUnaRespuestaSegura(String mensaje, Motivo motivo) {
        Optional<RespuestaSegura> respuesta = filtro.evaluar(mensaje);

        assertThat(respuesta).isPresent();
        assertThat(respuesta.get().motivo()).isEqualTo(motivo);
        // Remite a un profesional o al 112 y no da otros teléfonos
        assertThat(respuesta.get().texto()).containsAnyOf("profesional", "médico").contains("112");
        assertThat(respuesta.get().texto().replace("112", "")).doesNotContainPattern("\\d{3}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "¿qué ceno hoy?",
        "¿Cuánta proteína tomé ayer?",
        "¿La creatina es buena para correr?",
        "¿Cuánta cafeína tiene un café?",
        "¿Cómo prevenir lesiones al correr?",
        "Dame una cena de 600 kcal",
        "Quiero perder 1 kilo en una semana",
        "Resumen de mi semana",
        "¿Qué como antes de entrenar hoy?"
    })
    void lasPreguntasNormalesPasanAlModelo(String mensaje) {
        assertThat(filtro.evaluar(mensaje)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void unMensajeVacioNoEsSensible(String mensaje) {
        assertThat(filtro.evaluar(mensaje)).isEmpty();
    }

    @Test
    void conductaDeRiesgoTienePrioridadSobreLasOtras() {
        // Habla de pastillas, pero el riesgo es la conducta
        assertThat(filtro.evaluar("dejar de comer y tomar pastillas")).get()
                .extracting(RespuestaSegura::motivo).isEqualTo(Motivo.CONDUCTA_ALIMENTARIA_RIESGO);
    }
}
