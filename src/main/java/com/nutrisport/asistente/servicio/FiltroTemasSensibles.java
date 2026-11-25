package com.nutrisport.asistente.servicio;

import com.nutrisport.asistente.servicio.RespuestaSegura.Motivo;
import com.nutrisport.comun.util.TextoNormalizado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Filtro previo del asistente (§4.4): detecta con listas de expresiones los mensajes sobre conductas alimentarias de
 * riesgo, dosis de medicamentos o suplementos y consultas médicas, y devuelve una respuesta segura que se da SIN
 * llamar al modelo. Se comprueba en ese orden (la primera coincidencia manda).
 * <p>
 * Es deliberadamente conservador: solo intercepta los casos claros ("dieta de 500 kcal", "cuántos mg de ibuprofeno",
 * "estoy embarazada") para no bloquear preguntas normales ("¿la creatina es buena?", "¿cuánta proteína tomé
 * ayer?"); los casos dudosos los cubren las reglas del prompt de sistema. El texto se compara normalizado
 * (minúsculas y sin tildes). Las respuestas remiten a un profesional y al 112 ante una urgencia; no se dan otros
 * teléfonos.
 */
@Component
public class FiltroTemasSensibles {

    static final String TEXTO_CONDUCTA = "Lo que planteas puede ser perjudicial para tu salud y no puedo ayudarte con "
            + "ello. Te recomiendo hablarlo con un profesional sanitario, como tu médico de cabecera o un "
            + "dietista-nutricionista, que pueda acompañarte. Si en algún momento sientes que tu salud o tu vida "
            + "corren peligro, llama al 112.";
    static final String TEXTO_DOSIS = "No puedo indicarte dosis de medicamentos ni de suplementos. Pregúntaselo a tu "
            + "médico o a tu farmacéutico, que conocen tu situación. Ante una urgencia, llama al 112.";
    static final String TEXTO_MEDICO = "No puedo valorar síntomas, diagnósticos ni situaciones médicas como esta. "
            + "Consúltalo con tu médico o con un profesional sanitario; ante una urgencia, llama al 112. Si quieres, "
            + "puedo ayudarte con dudas generales de alimentación y entrenamiento.";

    // ---------------------------------------------------------------------------------------------
    // Conductas alimentarias de riesgo
    // ---------------------------------------------------------------------------------------------

    private static final List<Pattern> CONDUCTA = patrones(
            "\\b(vomitar|vomito|vomitos|provocarme el vomito|provocar el vomito)\\b",
            "\\b(laxantes?|diureticos?) (para|y) (adelgazar|perder peso|bajar de peso)\\b",
            "\\b(tomar|tomo|uso|usar) (laxantes?|diureticos?)\\b",
            "\\bpurgar(me)?\\b",
            "\\batracon(es)?\\b",
            "\\b(dejar de comer|no comer nada|sin comer nada|pasar hambre)\\b",
            "\\bayun(o|ar)\\b.{0,25}\\b(\\d+|dos|tres|cuatro|cinco|seis|siete|varios) dias\\b",
            // Ingestas muy bajas: "dieta de 500 kcal", "800 calorias al dia"
            "\\bdieta de [1-9]\\d{1,2} ?(kcal|calorias)\\b",
            "\\b[1-9]\\d{1,2} ?(kcal|calorias) (al dia|diarias|por dia)\\b");

    /** "perder 5 kilos en una semana" / "en 7 días": se comprueba la cantidad en {@link #perdidaRapida}. */
    private static final Pattern PERDIDA = Pattern.compile(
            "\\b(perder|bajar|adelgazar)\\s+(\\d+(?:[.,]\\d+)?)\\s*(kilos?|kg|kilogramos?)\\s+en\\s+(una|1|\\d+)\\s+"
                    + "(semana|dias)\\b");
    /** Más de 1 kg por semana es una pérdida demasiado rápida. */
    private static final BigDecimal MAXIMO_KG_SEMANA = BigDecimal.ONE;

    // ---------------------------------------------------------------------------------------------
    // Dosis de medicamentos o suplementos
    // ---------------------------------------------------------------------------------------------

    private static final String SUSTANCIAS = "(ibuprofeno|paracetamol|aspirina|antiinflamatorios?|naproxeno|"
            + "enantyum|nolotil|metamizol|medicamentos?|medicacion|farmacos?|pastillas?|comprimidos?|antibioticos?|"
            + "creatina|cafeina|melatonina|beta ?alanina|bicarbonato|hierro|magnesio|vitamina [a-z0-9]+|"
            + "suplementos?|pre ?entreno)";
    private static final String UNIDADES = "(dosis|mg|miligramos|microgramos|mcg)";
    private static final String TOMAR = "(tomo|tomar|me tomo|debo|deberia|puedo tomar|necesito|hay que tomar)";

    /**
     * Una dosis: "dosis" o una unidad (mg…) junto a la sustancia, o "cuánto… de [sustancia]… tomo/debo tomar". Una
     * pregunta sobre la sustancia sin pedir cantidad ("¿la creatina es buena?", "¿cuánta cafeína tiene un café?") no
     * se intercepta.
     */
    private static final List<Pattern> DOSIS = patrones(
            "\\b" + UNIDADES + "\\b.{0,40}\\b" + SUSTANCIAS + "\\b",
            "\\b" + SUSTANCIAS + "\\b.{0,40}\\b" + UNIDADES + "\\b",
            "\\b(cuant[oa]s?|que cantidad)\\b.{0,40}\\b" + SUSTANCIAS + "\\b.{0,40}\\b" + TOMAR + "\\b",
            "\\b" + TOMAR + "\\b.{0,20}\\b(cuant[oa]s?|que cantidad)\\b.{0,30}\\b" + SUSTANCIAS + "\\b",
            "\\b(esteroides|anabolizantes|clembuterol|testosterona|epo)\\b");

    // ---------------------------------------------------------------------------------------------
    // Consultas médicas
    // ---------------------------------------------------------------------------------------------

    private static final List<Pattern> MEDICO = patrones(
            "\\b(diagnostico|diagnosticar|diagnosticarme|sintomas?)\\b",
            "\\bme (duele|duelen|mareo|desmayo|ahogo)\\b",
            "\\bdolor (de|en el|en la) (pecho|cabeza|estomago|tripa|rodilla|espalda)\\b",
            "\\b(mareos?|desmayos?|desmaye|palpitaciones|taquicardias?)\\b",
            "\\b(embarazada|embarazo|lactancia|dando el pecho)\\b",
            "\\b(diabetes|diabetico|diabetica|insulina|hipertension|tension alta|enfermedad renal|"
                    + "insuficiencia renal|cancer|tiroides)\\b",
            "\\b((tengo|me he hecho|con) una lesion|lesionad[oa]|esguince|rotura de fibras)\\b");

    /**
     * @return la respuesta segura si el mensaje trata un tema sensible; vacío si se puede pasar al modelo
     */
    public Optional<RespuestaSegura> evaluar(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return Optional.empty();
        }
        String texto = TextoNormalizado.normalizar(mensaje);
        if (coincide(CONDUCTA, texto) || perdidaRapida(texto)) {
            return Optional.of(new RespuestaSegura(Motivo.CONDUCTA_ALIMENTARIA_RIESGO, TEXTO_CONDUCTA));
        }
        if (coincide(DOSIS, texto)) {
            return Optional.of(new RespuestaSegura(Motivo.DOSIS_MEDICAMENTOS, TEXTO_DOSIS));
        }
        if (coincide(MEDICO, texto)) {
            return Optional.of(new RespuestaSegura(Motivo.CONSULTA_MEDICA, TEXTO_MEDICO));
        }
        return Optional.empty();
    }

    /** Más de 1 kg por semana ("5 kilos en una semana", "3 kg en 10 dias"). */
    static boolean perdidaRapida(String texto) {
        Matcher coincidencia = PERDIDA.matcher(texto);
        while (coincidencia.find()) {
            BigDecimal kilos = new BigDecimal(coincidencia.group(2).replace(',', '.'));
            String cantidad = coincidencia.group(4);
            int dias = "semana".equals(coincidencia.group(5))
                    ? 7 * ("una".equals(cantidad) ? 1 : Integer.parseInt(cantidad))
                    : ("una".equals(cantidad) ? 1 : Integer.parseInt(cantidad));
            if (dias > 0 && kilos.multiply(BigDecimal.valueOf(7))
                    .compareTo(MAXIMO_KG_SEMANA.multiply(BigDecimal.valueOf(dias))) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincide(List<Pattern> patrones, String texto) {
        return patrones.stream().anyMatch(patron -> patron.matcher(texto).find());
    }

    private static List<Pattern> patrones(String... expresiones) {
        return java.util.Arrays.stream(expresiones).map(Pattern::compile).toList();
    }
}
