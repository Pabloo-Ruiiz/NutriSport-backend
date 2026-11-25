package com.nutrisport.seguimiento.servicio;

import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.seguimiento.entidad.SeveridadInsight;
import com.nutrisport.seguimiento.entidad.TipoInsight;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.DiaDatos;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.Pesaje;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Detección de patrones por reglas (§4.6, fase 1: estadística sin ML). Sin Spring ni base de datos y determinista:
 * recibe los datos del periodo (28 días) y devuelve los hallazgos con sus cifras ya calculadas, en este orden:
 * <table>
 * <caption>Reglas</caption>
 * <tr><th>Tipo</th><th>Condición</th><th>Severidad</th></tr>
 * <tr><td>PROTEINA_BAJA</td><td>proteína &lt; 80 % del objetivo en ≥ 4 de los últimos 7 días con registro</td>
 * <td>ADVERTENCIA</td></tr>
 * <tr><td>CARBOHIDRATOS_ENTRENO_BAJOS</td><td>en días de entreno, HC medios ≥ 15 % por debajo del objetivo
 * (mín. 3 días de entreno)</td><td>ADVERTENCIA</td></tr>
 * <tr><td>REPARTO_PROTEINA_BUENO</td><td>≥ 25 g de proteína en 3 o más comidas en ≥ 75 % de los días con
 * registro</td><td>POSITIVO</td></tr>
 * <tr><td>DIAS_SIN_REGISTRO</td><td>un día de la semana con ≥ 30 % menos registros que la media</td>
 * <td>INFO</td></tr>
 * <tr><td>TENDENCIA_PESO</td><td>pendiente de la regresión lineal del peso (≥ 4 pesajes) con signo distinto al
 * del objetivo</td><td>INFO</td></tr>
 * </table>
 * Precisiones:
 * <ul>
 * <li>Las comparaciones con un objetivo solo usan los días que tienen ObjetivoDiario; los de entreno y los de
 * proteína, además, solo los días con registro (sin comidas no hay consumo que comparar).</li>
 * <li>DIAS_SIN_REGISTRO compara los registros medios de cada día de la semana (por cada vez que aparece en el periodo)
 * con los registros medios por día del periodo; con 28 días equivale a comparar totales. Si hay varios días por
 * debajo, el que menos registra.</li>
 * <li>TENDENCIA_PESO: solo PERDER_GRASA (se espera que baje) y GANAR_MUSCULO (que suba) tienen signo; con los demás
 * objetivos o sin objetivo no se aplica. La pendiente se redondea a centésimas de kg por semana antes de mirar su
 * signo (una pendiente que se queda en 0,00 también es «distinta» del signo esperado).</li>
 * <li>Las cifras enteras van como Integer redondeadas a la unidad (media arriba) y la pendiente, como BigDecimal.</li>
 * </ul>
 */
public final class DetectorPatrones {

    static final int DIAS_EVALUADOS_PROTEINA = 7;
    static final int MINIMO_DIAS_PROTEINA_BAJA = 4;
    static final BigDecimal FRACCION_PROTEINA_BAJA = new BigDecimal("0.80");

    static final int MINIMO_DIAS_ENTRENO = 3;
    static final BigDecimal DEFICIT_CARBOHIDRATOS_PCT = new BigDecimal("15");

    static final BigDecimal PROTEINA_POR_COMIDA_G = new BigDecimal("25");
    static final int MINIMO_COMIDAS_CON_PROTEINA = 3;
    static final BigDecimal FRACCION_DIAS_REPARTO = new BigDecimal("0.75");

    static final BigDecimal FRACCION_DIA_SEMANA = new BigDecimal("0.70");

    static final int MINIMO_PESAJES = 4;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal DIAS_POR_SEMANA = new BigDecimal("7");
    private static final Locale ES = Locale.forLanguageTag("es-ES");

    public DetectorPatrones() {
    }

    /**
     * @param objetivo objetivo deportivo del perfil; nulo si no hay perfil (entonces no se mira la tendencia del peso)
     * @return los hallazgos en el orden de la tabla; vacía si no se cumple ninguna regla
     */
    public List<Hallazgo> detectar(DatosPeriodo datos, ObjetivoDeportivo objetivo) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        proteinaBaja(datos.dias()).ifPresent(hallazgos::add);
        carbohidratosEntrenoBajos(datos.dias()).ifPresent(hallazgos::add);
        repartoProteinaBueno(datos.dias()).ifPresent(hallazgos::add);
        diasSinRegistro(datos.dias()).ifPresent(hallazgos::add);
        tendenciaPeso(datos.pesajes(), objetivo).ifPresent(hallazgos::add);
        return hallazgos;
    }

    // ---------------------------------------------------------------------------------------------
    // Reglas
    // ---------------------------------------------------------------------------------------------

    /** Proteína &lt; 80 % del objetivo en ≥ 4 de los últimos 7 días con registro. */
    static Optional<Hallazgo> proteinaBaja(List<DiaDatos> dias) {
        List<DiaDatos> conRegistro = dias.stream().filter(DiaDatos::conRegistro).toList();
        List<DiaDatos> ultimos = conRegistro.subList(Math.max(0, conRegistro.size() - DIAS_EVALUADOS_PROTEINA),
                conRegistro.size());
        List<DiaDatos> conObjetivo = ultimos.stream()
                .filter(dia -> dia.objetivo() != null && dia.objetivo().proteina().signum() > 0)
                .toList();
        long bajos = conObjetivo.stream()
                .filter(dia -> dia.consumido().proteina()
                        .compareTo(dia.objetivo().proteina().multiply(FRACCION_PROTEINA_BAJA)) < 0)
                .count();
        if (bajos < MINIMO_DIAS_PROTEINA_BAJA) {
            return Optional.empty();
        }
        BigDecimal consumida = media(conObjetivo, dia -> dia.consumido().proteina());
        BigDecimal recomendada = media(conObjetivo, dia -> dia.objetivo().proteina());
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("diasBajos", (int) bajos);
        cifras.put("diasEvaluados", ultimos.size());
        cifras.put("proteinaMediaG", entero(consumida));
        cifras.put("objetivoProteinaG", entero(recomendada));
        cifras.put("porcentajeObjetivo", entero(consumida.multiply(CIEN).divide(recomendada, MathContext.DECIMAL64)));
        cifras.put("gramosFaltantes", entero(recomendada.subtract(consumida).max(BigDecimal.ZERO)));
        return Optional.of(new Hallazgo(TipoInsight.PROTEINA_BAJA, SeveridadInsight.ADVERTENCIA, cifras));
    }

    /** En los días de entreno (con registro y objetivo, mínimo 3), HC medios ≥ 15 % por debajo del objetivo medio. */
    static Optional<Hallazgo> carbohidratosEntrenoBajos(List<DiaDatos> dias) {
        List<DiaDatos> entreno = dias.stream()
                .filter(dia -> dia.diaEntreno() && dia.conRegistro() && dia.objetivo() != null
                        && dia.objetivo().carbohidratos().signum() > 0)
                .toList();
        if (entreno.size() < MINIMO_DIAS_ENTRENO) {
            return Optional.empty();
        }
        BigDecimal consumidos = media(entreno, dia -> dia.consumido().carbohidratos());
        BigDecimal recomendados = media(entreno, dia -> dia.objetivo().carbohidratos());
        BigDecimal diferenciaPct = recomendados.subtract(consumidos).multiply(CIEN)
                .divide(recomendados, MathContext.DECIMAL64);
        if (diferenciaPct.compareTo(DEFICIT_CARBOHIDRATOS_PCT) < 0) {
            return Optional.empty();
        }
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("diasEntreno", entreno.size());
        cifras.put("carbohidratosMediosG", entero(consumidos));
        cifras.put("objetivoCarbohidratosG", entero(recomendados));
        cifras.put("diferenciaPct", entero(diferenciaPct));
        cifras.put("gramosFaltantes", entero(recomendados.subtract(consumidos)));
        return Optional.of(new Hallazgo(TipoInsight.CARBOHIDRATOS_ENTRENO_BAJOS, SeveridadInsight.ADVERTENCIA,
                cifras));
    }

    /** ≥ 25 g de proteína en 3 o más comidas en ≥ 75 % de los días con registro. */
    static Optional<Hallazgo> repartoProteinaBueno(List<DiaDatos> dias) {
        List<DiaDatos> conRegistro = dias.stream().filter(DiaDatos::conRegistro).toList();
        if (conRegistro.isEmpty()) {
            return Optional.empty();
        }
        long buenos = conRegistro.stream()
                .filter(dia -> dia.proteinaPorComida().stream()
                        .filter(gramos -> gramos.compareTo(PROTEINA_POR_COMIDA_G) >= 0)
                        .count() >= MINIMO_COMIDAS_CON_PROTEINA)
                .count();
        BigDecimal total = BigDecimal.valueOf(conRegistro.size());
        if (BigDecimal.valueOf(buenos).compareTo(total.multiply(FRACCION_DIAS_REPARTO)) < 0) {
            return Optional.empty();
        }
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("diasBuenos", (int) buenos);
        cifras.put("diasConRegistro", conRegistro.size());
        cifras.put("porcentajeDias",
                entero(BigDecimal.valueOf(buenos).multiply(CIEN).divide(total, MathContext.DECIMAL64)));
        return Optional.of(new Hallazgo(TipoInsight.REPARTO_PROTEINA_BUENO, SeveridadInsight.POSITIVO, cifras));
    }

    /** Un día de la semana con ≥ 30 % menos registros que la media (el que menos, si hay varios). */
    static Optional<Hallazgo> diasSinRegistro(List<DiaDatos> dias) {
        int totalRegistros = dias.stream().mapToInt(DiaDatos::registros).sum();
        if (dias.isEmpty() || totalRegistros == 0) {
            return Optional.empty();
        }
        BigDecimal mediaPorDia = BigDecimal.valueOf(totalRegistros)
                .divide(BigDecimal.valueOf(dias.size()), MathContext.DECIMAL64);
        Map<DayOfWeek, int[]> porDiaSemana = new EnumMap<>(DayOfWeek.class);
        for (DiaDatos dia : dias) {
            int[] acumulado = porDiaSemana.computeIfAbsent(dia.fecha().getDayOfWeek(), d -> new int[2]);
            acumulado[0] += dia.registros();
            acumulado[1]++;
        }
        DayOfWeek peor = null;
        BigDecimal mediaPeor = null;
        // EnumMap recorre de lunes a domingo: a igualdad, el primero
        for (Map.Entry<DayOfWeek, int[]> diaSemana : porDiaSemana.entrySet()) {
            BigDecimal mediaDia = BigDecimal.valueOf(diaSemana.getValue()[0])
                    .divide(BigDecimal.valueOf(diaSemana.getValue()[1]), MathContext.DECIMAL64);
            if (mediaDia.compareTo(mediaPorDia.multiply(FRACCION_DIA_SEMANA)) <= 0
                    && (mediaPeor == null || mediaDia.compareTo(mediaPeor) < 0)) {
                peor = diaSemana.getKey();
                mediaPeor = mediaDia;
            }
        }
        if (peor == null) {
            return Optional.empty();
        }
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("diaSemana", peor.getValue());
        cifras.put("nombreDia", peor.getDisplayName(TextStyle.FULL, ES));
        cifras.put("registrosDia", mediaPeor.setScale(1, RoundingMode.HALF_UP));
        cifras.put("registrosMedia", mediaPorDia.setScale(1, RoundingMode.HALF_UP));
        cifras.put("diferenciaPct",
                entero(mediaPorDia.subtract(mediaPeor).multiply(CIEN).divide(mediaPorDia, MathContext.DECIMAL64)));
        return Optional.of(new Hallazgo(TipoInsight.DIAS_SIN_REGISTRO, SeveridadInsight.INFO, cifras));
    }

    /** Pendiente de la regresión lineal del peso (≥ 4 pesajes) con signo distinto al del objetivo. */
    static Optional<Hallazgo> tendenciaPeso(List<Pesaje> pesajes, ObjetivoDeportivo objetivo) {
        int esperado = signoEsperado(objetivo);
        if (esperado == 0 || pesajes.size() < MINIMO_PESAJES) {
            return Optional.empty();
        }
        BigDecimal pendienteDia = pendiente(pesajes);
        if (pendienteDia == null) {
            return Optional.empty();
        }
        BigDecimal pendienteSemana = pendienteDia.multiply(DIAS_POR_SEMANA).setScale(2, RoundingMode.HALF_UP);
        if (pendienteSemana.signum() == esperado) {
            return Optional.empty();
        }
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("pendienteKgSemana", pendienteSemana);
        cifras.put("pesajes", pesajes.size());
        cifras.put("pesoInicialKg", pesajes.get(0).pesoKg());
        cifras.put("pesoFinalKg", pesajes.get(pesajes.size() - 1).pesoKg());
        cifras.put("objetivo", objetivo.name());
        cifras.put("tendencia", switch (pendienteSemana.signum()) {
            case 1 -> "sube";
            case -1 -> "baja";
            default -> "estable";
        });
        return Optional.of(new Hallazgo(TipoInsight.TENDENCIA_PESO, SeveridadInsight.INFO, cifras));
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** −1 si el objetivo busca bajar de peso, +1 si busca subir, 0 si no tiene dirección (o no hay objetivo). */
    static int signoEsperado(ObjetivoDeportivo objetivo) {
        if (objetivo == null) {
            return 0;
        }
        return switch (objetivo) {
            case PERDER_GRASA -> -1;
            case GANAR_MUSCULO -> 1;
            case MANTENER, RENDIMIENTO, COMPETICION -> 0;
        };
    }

    /**
     * Pendiente en kg/día de la recta de mínimos cuadrados del peso frente a los días desde el primer pesaje; nula si
     * todos los pesajes son del mismo día.
     */
    static BigDecimal pendiente(List<Pesaje> pesajes) {
        Pesaje primero = pesajes.get(0);
        int n = pesajes.size();
        double sumaX = 0;
        double sumaY = 0;
        for (Pesaje pesaje : pesajes) {
            sumaX += ChronoUnit.DAYS.between(primero.fecha(), pesaje.fecha());
            sumaY += pesaje.pesoKg().doubleValue();
        }
        double mediaX = sumaX / n;
        double mediaY = sumaY / n;
        double covarianza = 0;
        double varianza = 0;
        for (Pesaje pesaje : pesajes) {
            double x = ChronoUnit.DAYS.between(primero.fecha(), pesaje.fecha()) - mediaX;
            covarianza += x * (pesaje.pesoKg().doubleValue() - mediaY);
            varianza += x * x;
        }
        if (varianza == 0) {
            return null;
        }
        return BigDecimal.valueOf(covarianza / varianza);
    }

    private static BigDecimal media(List<DiaDatos> dias, Function<DiaDatos, BigDecimal> valor) {
        return dias.stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dias.size()), MathContext.DECIMAL64);
    }

    private static int entero(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
