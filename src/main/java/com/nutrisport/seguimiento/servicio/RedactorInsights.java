package com.nutrisport.seguimiento.servicio;

import com.nutrisport.comun.config.IaProperties;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.UsoIaServicio;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Título y texto de un insight a partir de un {@link Hallazgo} (§4.6): plantillas en castellano por tipo que insertan
 * las cifras ya calculadas de {@code datos}, seguidas de un consejo práctico.
 * <p>
 * El consejo es de plantilla salvo con {@code app.ia.modo=real}: entonces se pide a la IA (prompts/consejo-insight.st)
 * una sola frase de como mucho 160 caracteres con el tipo y las cifras, indicándole que no añada números. Si la IA
 * falla, no responde nada, se pasa de largo o escribe algún número que no esté en los datos, se usa el de plantilla.
 * La IA nunca escribe el diagnóstico ni las cifras.
 */
@Component
public class RedactorInsights {

    static final String PLANTILLA_CONSEJO = "consejo-insight";
    static final int LONGITUD_MAXIMA_CONSEJO = 160;
    static final int LONGITUD_MAXIMA_TITULO = 120;
    static final int LONGITUD_MAXIMA_TEXTO = 600;

    private static final Pattern NUMERO = Pattern.compile("\\d+");
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final Logger LOG = LoggerFactory.getLogger(RedactorInsights.class);

    /** Título y texto (diagnóstico con cifras y consejo) de un insight. */
    public record Redaccion(String titulo, String texto) {
    }

    private final GeneracionTextoPuerto generacionTexto;
    private final UsoIaServicio usoIaServicio;
    private final IaProperties iaProperties;

    public RedactorInsights(GeneracionTextoPuerto generacionTexto, UsoIaServicio usoIaServicio,
            IaProperties iaProperties) {
        this.generacionTexto = generacionTexto;
        this.usoIaServicio = usoIaServicio;
        this.iaProperties = iaProperties;
    }

    /** Sin usuario para el registro de uso de la IA (llamada del sistema). */
    public Redaccion redactar(Hallazgo hallazgo) {
        return redactar(hallazgo, null);
    }

    /** @param usuarioId para el registro de uso de la IA; nulo en llamadas del sistema */
    public Redaccion redactar(Hallazgo hallazgo, UUID usuarioId) {
        Map<String, Object> d = hallazgo.datos();
        String titulo;
        String diagnostico;
        switch (hallazgo.tipo()) {
            case PROTEINA_BAJA -> {
                titulo = "Te falta proteína";
                diagnostico = "En " + d.get("diasBajos") + " de tus últimos " + d.get("diasEvaluados")
                        + " días con registro tomaste menos del 80 % de la proteína recomendada: de media "
                        + d.get("proteinaMediaG") + " g de " + d.get("objetivoProteinaG") + " g ("
                        + d.get("porcentajeObjetivo") + " %), unos " + d.get("gramosFaltantes") + " g menos al día.";
            }
            case CARBOHIDRATOS_ENTRENO_BAJOS -> {
                titulo = "Pocos carbohidratos los días de entreno";
                diagnostico = "En tus días de entreno tomas de media un " + d.get("diferenciaPct")
                        + " % menos de carbohidratos de lo recomendado: " + d.get("carbohidratosMediosG") + " g de "
                        + d.get("objetivoCarbohidratosG") + " g, unos " + d.get("gramosFaltantes")
                        + " g menos (" + d.get("diasEntreno") + " días de entreno analizados).";
            }
            case REPARTO_PROTEINA_BUENO -> {
                titulo = "Buen reparto de la proteína";
                diagnostico = "En " + d.get("diasBuenos") + " de " + d.get("diasConRegistro") + " días ("
                        + d.get("porcentajeDias") + " %) has tomado al menos 25 g de proteína en 3 o más comidas.";
            }
            case DIAS_SIN_REGISTRO -> {
                String plural = plural(String.valueOf(d.get("nombreDia")));
                titulo = "Los " + plural + " registras menos";
                diagnostico = "Los " + plural + " registras de media " + numero(d.get("registrosDia"))
                        + " comidas, un " + d.get("diferenciaPct") + " % menos que tu media de "
                        + numero(d.get("registrosMedia")) + " al día.";
            }
            case TENDENCIA_PESO -> {
                titulo = "Tu peso va en otra dirección";
                BigDecimal pendiente = (BigDecimal) d.get("pendienteKgSemana");
                String movimiento = switch (String.valueOf(d.get("tendencia"))) {
                    case "sube" -> "sube unos " + numero(pendiente.abs()) + " kg por semana";
                    case "baja" -> "baja unos " + numero(pendiente.abs()) + " kg por semana";
                    default -> "se mantiene estable";
                };
                diagnostico = "Tu objetivo es " + objetivo(String.valueOf(d.get("objetivo"))) + ", pero tu peso "
                        + movimiento + " (" + d.get("pesajes") + " pesajes: de " + numero(d.get("pesoInicialKg"))
                        + " a " + numero(d.get("pesoFinalKg")) + " kg).";
            }
            default -> throw new IllegalArgumentException("Tipo de insight sin plantilla: " + hallazgo.tipo());
        }
        String texto = diagnostico + " " + consejo(hallazgo, diagnostico, usuarioId);
        return new Redaccion(recortar(titulo, LONGITUD_MAXIMA_TITULO), recortar(texto, LONGITUD_MAXIMA_TEXTO));
    }

    // ---------------------------------------------------------------------------------------------
    // Consejo
    // ---------------------------------------------------------------------------------------------

    /** El de la IA en modo real si es válido; si no, el de plantilla. */
    private String consejo(Hallazgo hallazgo, String diagnostico, UUID usuarioId) {
        if (iaProperties.modo() != IaProperties.Modo.REAL) {
            return consejoDePlantilla(hallazgo);
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("tipo", hallazgo.tipo().name());
        variables.put("descripcion", diagnostico);
        variables.put("cifras", hallazgo.datos().entrySet().stream()
                .map(cifra -> cifra.getKey() + ": " + cifra.getValue())
                .collect(Collectors.joining("\n")));
        long inicio = System.nanoTime();
        try {
            ResultadoIa<String> resultado = generacionTexto.generar(PLANTILLA_CONSEJO, variables);
            usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, resultado, milisegundosDesde(inicio), true);
            String propuesto = limpiar(resultado.contenido());
            if (consejoValido(propuesto, hallazgo.datos())) {
                return propuesto;
            }
            LOG.info("Consejo de la IA descartado para {} (vacío, largo o con cifras nuevas); se usa el de plantilla",
                    hallazgo.tipo());
        } catch (RuntimeException e) {
            LOG.warn("No se ha podido pedir el consejo del insight a la IA; se usa el de plantilla: {}", e.toString());
            try {
                usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, null, milisegundosDesde(inicio), false);
            } catch (RuntimeException registro) {
                LOG.warn("No se ha podido registrar el uso fallido de la IA: {}", registro.toString());
            }
        }
        return consejoDePlantilla(hallazgo);
    }

    /**
     * Un consejo de la IA vale si no está vacío, tiene como mucho 160 caracteres y todos sus números aparecen en los
     * datos del hallazgo (en cualquiera de sus valores).
     */
    static boolean consejoValido(String consejo, Map<String, Object> datos) {
        if (consejo == null || consejo.isBlank() || consejo.length() > LONGITUD_MAXIMA_CONSEJO) {
            return false;
        }
        Set<String> permitidos = datos.values().stream()
                .map(valor -> valor instanceof BigDecimal decimal ? decimal.toPlainString() : String.valueOf(valor))
                .flatMap(valor -> NUMERO.matcher(valor).results().map(MatchResult::group))
                .collect(Collectors.toSet());
        return NUMERO.matcher(consejo).results().map(MatchResult::group).allMatch(permitidos::contains);
    }

    /** Una sola línea, sin espacios ni comillas alrededor. */
    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String linea = texto.strip().lines().findFirst().orElse("").strip();
        return linea.replaceAll("^[\"'«“]+|[\"'»”]+$", "").strip();
    }

    static String consejoDePlantilla(Hallazgo hallazgo) {
        return switch (hallazgo.tipo()) {
            case PROTEINA_BAJA -> "Añade una fuente de proteína (huevos, legumbres, yogur o pescado) a una comida "
                    + "más del día.";
            case CARBOHIDRATOS_ENTRENO_BAJOS -> "Prueba con una merienda pre-entreno con hidratos, como fruta, pan "
                    + "o copos de avena.";
            case REPARTO_PROTEINA_BUENO -> "Mantén este patrón: repartir la proteína a lo largo del día ayuda a "
                    + "recuperar.";
            case DIAS_SIN_REGISTRO -> "Si ese día comes fuera, prueba a registrar la comida con una foto.";
            case TENDENCIA_PESO -> "GANAR_MUSCULO".equals(hallazgo.datos().get("objetivo"))
                    ? "Revisa que comes lo suficiente los días de entreno y no te saltes comidas."
                    : "Revisa las raciones y los extras del fin de semana: un ajuste pequeño y constante basta.";
        };
    }

    // ---------------------------------------------------------------------------------------------

    /** Un número con coma decimal y sin ceros finales: 2.70 → "2,7", 1.0 → "1". */
    static String numero(Object valor) {
        if (valor instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString().replace('.', ',');
        }
        return String.valueOf(valor);
    }

    /** "domingo" → "domingos"; "lunes" se queda igual. */
    static String plural(String dia) {
        return dia.endsWith("s") ? dia : dia + "s";
    }

    private static String objetivo(String objetivo) {
        return switch (objetivo) {
            case "PERDER_GRASA" -> "perder grasa";
            case "GANAR_MUSCULO" -> "ganar músculo";
            default -> objetivo.toLowerCase(ES).replace('_', ' ');
        };
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1).strip() + "…";
    }

    private static long milisegundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
