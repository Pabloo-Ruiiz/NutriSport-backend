package com.nutrisport.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.seguimiento.entidad.SeveridadInsight;
import com.nutrisport.seguimiento.entidad.TipoInsight;
import com.nutrisport.seguimiento.servicio.DatosPeriodo;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.DiaDatos;
import com.nutrisport.seguimiento.servicio.DatosPeriodo.Pesaje;
import com.nutrisport.seguimiento.servicio.DetectorPatrones;
import com.nutrisport.seguimiento.servicio.Hallazgo;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Reglas de §4.6 con datos sintéticos de 28 días (del lunes 26 de octubre al domingo 22 de noviembre de 2026).
 * <p>
 * El punto de partida son datos «perfectos», que no producen ningún hallazgo: cada día se come exactamente el
 * objetivo (2500 kcal, 150 g de proteína, 300 g de hidratos, 70 g de grasa) en 4 comidas con 60, 60, 15 y 15 g de
 * proteína (solo 2 llegan a 25 g), se entrena lunes, miércoles y viernes, y el peso baja con el objetivo PERDER_GRASA.
 * Cada prueba cambia solo lo que quiere detectar.
 */
class DetectorPatronesTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 26);
    private static final int DIAS = 28;
    private static final Nutrientes OBJETIVO = nutrientes("2500", "150", "300", "70");
    private static final Set<DayOfWeek> DIAS_ENTRENO = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY);
    private static final List<BigDecimal> COMIDAS_PERFECTAS = gramos(60, 60, 15, 15);

    private final DetectorPatrones detector = new DetectorPatrones();

    // ---------------------------------------------------------------------------------------------
    // Datos perfectos
    // ---------------------------------------------------------------------------------------------

    @Test
    void conDatosPerfectosNoHayNingunHallazgo() {
        assertThat(detector.detectar(datos(dias(), pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA)).isEmpty();
        // Ni con los demás objetivos, ni sin perfil
        for (ObjetivoDeportivo objetivo : ObjetivoDeportivo.values()) {
            assertThat(detector.detectar(datos(dias(), List.of()), objetivo)).as(objetivo.name()).isEmpty();
        }
        assertThat(detector.detectar(datos(dias(), pesajesBajando()), null)).isEmpty();
        // Ni sin ningún registro
        List<DiaDatos> vacios = new ArrayList<>();
        dias().forEach(dia -> vacios.add(new DiaDatos(dia.fecha(), Nutrientes.CERO, OBJETIVO, false, List.of())));
        assertThat(detector.detectar(datos(vacios, List.of()), ObjetivoDeportivo.MANTENER)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // PROTEINA_BAJA
    // ---------------------------------------------------------------------------------------------

    @Test
    void proteinaBajaCon4DeLosUltimos7DiasPorDebajoDel80PorCiento() {
        List<DiaDatos> dias = dias();
        // 100 g de 150 (67 %) en 4 de los 7 últimos días
        for (int i : new int[] {21, 23, 25, 27}) {
            dias.set(i, conProteina(dias.get(i), 100));
        }

        Hallazgo hallazgo = unico(detector.detectar(datos(dias, pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA));

        assertThat(hallazgo.tipo()).isEqualTo(TipoInsight.PROTEINA_BAJA);
        assertThat(hallazgo.severidad()).isEqualTo(SeveridadInsight.ADVERTENCIA);
        // Media de los 7: (4 × 100 + 3 × 150) / 7 = 121,4 g → 81 % y 28,6 g menos al día
        assertThat(hallazgo.datos())
                .containsEntry("diasBajos", 4)
                .containsEntry("diasEvaluados", 7)
                .containsEntry("proteinaMediaG", 121)
                .containsEntry("objetivoProteinaG", 150)
                .containsEntry("porcentajeObjetivo", 81)
                .containsEntry("gramosFaltantes", 29);
    }

    @Test
    void con3De7DiasNoHayProteinaBaja() {
        List<DiaDatos> dias = dias();
        for (int i : new int[] {21, 24, 27}) {
            dias.set(i, conProteina(dias.get(i), 100));
        }
        assertThat(tipos(detector.detectar(datos(dias, pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA)))
                .doesNotContain(TipoInsight.PROTEINA_BAJA);

        // Justo el 80 % (120 g) no es «por debajo»
        List<DiaDatos> alLimite = dias();
        for (int i = 21; i < DIAS; i++) {
            alLimite.set(i, conProteina(alLimite.get(i), 120));
        }
        assertThat(tipos(detector.detectar(datos(alLimite, List.of()), ObjetivoDeportivo.MANTENER)))
                .doesNotContain(TipoInsight.PROTEINA_BAJA);
    }

    @Test
    void soloCuentanLosUltimos7DiasConRegistro() {
        // 4 días bajos al principio del periodo: quedan fuera de los últimos 7
        List<DiaDatos> antiguos = dias();
        for (int i = 0; i < 4; i++) {
            antiguos.set(i, conProteina(antiguos.get(i), 100));
        }
        assertThat(tipos(detector.detectar(datos(antiguos, List.of()), ObjetivoDeportivo.MANTENER)))
                .doesNotContain(TipoInsight.PROTEINA_BAJA);

        // Sin registros los 3 últimos días, los 7 últimos con registro son del 18 al 24: ahí están los 4 bajos
        List<DiaDatos> conHueco = dias();
        for (int i = 25; i < DIAS; i++) {
            conHueco.set(i, sinRegistro(conHueco.get(i)));
        }
        for (int i : new int[] {18, 19, 20, 21}) {
            conHueco.set(i, conProteina(conHueco.get(i), 100));
        }
        Optional<Hallazgo> proteina = buscar(detector.detectar(datos(conHueco, List.of()), ObjetivoDeportivo.MANTENER),
                TipoInsight.PROTEINA_BAJA);
        assertThat(proteina).isPresent();
        assertThat(proteina.get().datos()).containsEntry("diasBajos", 4).containsEntry("diasEvaluados", 7);
    }

    // ---------------------------------------------------------------------------------------------
    // CARBOHIDRATOS_ENTRENO_BAJOS
    // ---------------------------------------------------------------------------------------------

    @Test
    void carbohidratosBajosCon4DiasDeEntrenoAl80PorCiento() {
        // Solo se entrena 4 días, y esos días se toman 240 g de 300 (80 %)
        List<DiaDatos> dias = dias(dia -> dia, Set.of());
        for (int i : new int[] {2, 9, 16, 23}) {
            dias.set(i, entrenoConHidratos(dias.get(i), 240));
        }

        Hallazgo hallazgo = unico(detector.detectar(datos(dias, pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA));

        assertThat(hallazgo.tipo()).isEqualTo(TipoInsight.CARBOHIDRATOS_ENTRENO_BAJOS);
        assertThat(hallazgo.severidad()).isEqualTo(SeveridadInsight.ADVERTENCIA);
        assertThat(hallazgo.datos())
                .containsEntry("diasEntreno", 4)
                .containsEntry("carbohidratosMediosG", 240)
                .containsEntry("objetivoCarbohidratosG", 300)
                .containsEntry("diferenciaPct", 20)
                .containsEntry("gramosFaltantes", 60);
    }

    @Test
    void losCarbohidratosDeEntrenoNecesitan3DiasYUn15PorCientoDeDeficit() {
        // Con solo 2 días de entreno no se evalúa, aunque estén a la mitad
        List<DiaDatos> dosDias = dias(dia -> dia, Set.of());
        dosDias.set(2, entrenoConHidratos(dosDias.get(2), 150));
        dosDias.set(9, entrenoConHidratos(dosDias.get(9), 150));
        assertThat(detector.detectar(datos(dosDias, List.of()), ObjetivoDeportivo.MANTENER)).isEmpty();

        // 255 g de 300 = 15 % por debajo: sí (≥ 15 %); 258 g = 14 %: no
        assertThat(tipos(detector.detectar(datos(entrenoAl(255), List.of()), ObjetivoDeportivo.MANTENER)))
                .containsExactly(TipoInsight.CARBOHIDRATOS_ENTRENO_BAJOS);
        assertThat(detector.detectar(datos(entrenoAl(258), List.of()), ObjetivoDeportivo.MANTENER)).isEmpty();

        // Los hidratos bajos en días de descanso no cuentan
        List<DiaDatos> descanso = dias(dia -> DIAS_ENTRENO.contains(dia.fecha().getDayOfWeek()) ? dia
                : conHidratos(dia, 150));
        assertThat(tipos(detector.detectar(datos(descanso, List.of()), ObjetivoDeportivo.MANTENER)))
                .doesNotContain(TipoInsight.CARBOHIDRATOS_ENTRENO_BAJOS);
    }

    // ---------------------------------------------------------------------------------------------
    // REPARTO_PROTEINA_BUENO
    // ---------------------------------------------------------------------------------------------

    @Test
    void repartoBuenoCon3ComidasDe25GramosEnEl75PorCientoDeLosDias() {
        // 21 de 28 días (75 %) con 30, 30, 30 y 60 g (la misma proteína total)
        List<DiaDatos> dias = dias();
        for (int i = 0; i < 21; i++) {
            dias.set(i, conComidas(dias.get(i), gramos(30, 30, 30, 60)));
        }

        Hallazgo hallazgo = unico(detector.detectar(datos(dias, pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA));

        assertThat(hallazgo.tipo()).isEqualTo(TipoInsight.REPARTO_PROTEINA_BUENO);
        assertThat(hallazgo.severidad()).isEqualTo(SeveridadInsight.POSITIVO);
        assertThat(hallazgo.datos())
                .containsEntry("diasBuenos", 21)
                .containsEntry("diasConRegistro", 28)
                .containsEntry("porcentajeDias", 75);

        // 20 de 28 (71 %) no llega; 25 g justos sí cuentan
        List<DiaDatos> veinte = dias();
        for (int i = 0; i < 20; i++) {
            veinte.set(i, conComidas(veinte.get(i), gramos(25, 25, 25, 75)));
        }
        assertThat(detector.detectar(datos(veinte, List.of()), ObjetivoDeportivo.MANTENER)).isEmpty();
        veinte.set(20, conComidas(veinte.get(20), gramos(25, 25, 25, 75)));
        assertThat(tipos(detector.detectar(datos(veinte, List.of()), ObjetivoDeportivo.MANTENER)))
                .containsExactly(TipoInsight.REPARTO_PROTEINA_BUENO);
    }

    // ---------------------------------------------------------------------------------------------
    // DIAS_SIN_REGISTRO
    // ---------------------------------------------------------------------------------------------

    @Test
    void unDiaDeLaSemanaConUn30PorCientoMenosDeRegistros() {
        // Los domingos, 1 comida en vez de 4
        List<DiaDatos> dias = dias(dia -> dia.fecha().getDayOfWeek() == DayOfWeek.SUNDAY
                ? conComidas(dia, gramos(150)) : dia);

        Hallazgo hallazgo = unico(detector.detectar(datos(dias, pesajesBajando()), ObjetivoDeportivo.PERDER_GRASA));

        assertThat(hallazgo.tipo()).isEqualTo(TipoInsight.DIAS_SIN_REGISTRO);
        assertThat(hallazgo.severidad()).isEqualTo(SeveridadInsight.INFO);
        // 100 comidas en 28 días = 3,57 al día; el domingo 1 → 72 % menos
        assertThat(hallazgo.datos())
                .containsEntry("diaSemana", 7)
                .containsEntry("nombreDia", "domingo")
                .containsEntry("registrosDia", new BigDecimal("1.0"))
                .containsEntry("registrosMedia", new BigDecimal("3.6"))
                .containsEntry("diferenciaPct", 72);

        // Los domingos con 3 comidas: media 3,86 y 3 / 3,86 = 78 % → solo un 22 % menos, no basta
        List<DiaDatos> tres = dias(dia -> dia.fecha().getDayOfWeek() == DayOfWeek.SUNDAY
                ? conComidas(dia, gramos(60, 60, 30)) : dia);
        assertThat(detector.detectar(datos(tres, List.of()), ObjetivoDeportivo.MANTENER)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // TENDENCIA_PESO
    // ---------------------------------------------------------------------------------------------

    @Test
    void tendenciaDelPesoContrariaAPerderGrasa() {
        // Sube 0,7 kg en 3 semanas: pendiente 8,4 / 245 kg/día = 0,24 kg/semana
        List<Pesaje> subiendo = pesajes("70.0", "70.2", "70.5", "70.7");

        Hallazgo hallazgo = unico(detector.detectar(datos(dias(), subiendo), ObjetivoDeportivo.PERDER_GRASA));

        assertThat(hallazgo.tipo()).isEqualTo(TipoInsight.TENDENCIA_PESO);
        assertThat(hallazgo.severidad()).isEqualTo(SeveridadInsight.INFO);
        assertThat(hallazgo.datos())
                .containsEntry("pendienteKgSemana", new BigDecimal("0.24"))
                .containsEntry("pesajes", 4)
                .containsEntry("pesoInicialKg", new BigDecimal("70.0"))
                .containsEntry("pesoFinalKg", new BigDecimal("70.7"))
                .containsEntry("objetivo", "PERDER_GRASA")
                .containsEntry("tendencia", "sube");

        // Con GANAR_MUSCULO lo contrario es bajar
        Hallazgo bajando = unico(detector.detectar(datos(dias(), pesajesBajando()), ObjetivoDeportivo.GANAR_MUSCULO));
        assertThat(bajando.datos()).containsEntry("tendencia", "baja").containsEntry("objetivo", "GANAR_MUSCULO");
        assertThat((BigDecimal) bajando.datos().get("pendienteKgSemana")).isNegative();
    }

    @Test
    void laTendenciaDelPesoNecesita4PesajesYUnObjetivoConDireccion() {
        List<Pesaje> subiendo = pesajes("70.0", "70.2", "70.5", "70.7");
        // 3 pesajes no bastan
        assertThat(detector.detectar(datos(dias(), subiendo.subList(0, 3)), ObjetivoDeportivo.PERDER_GRASA))
                .isEmpty();
        // Subir con GANAR_MUSCULO va con el objetivo
        assertThat(detector.detectar(datos(dias(), subiendo), ObjetivoDeportivo.GANAR_MUSCULO)).isEmpty();
        // MANTENER, RENDIMIENTO y COMPETICION no tienen dirección; sin perfil tampoco se mira
        for (ObjetivoDeportivo objetivo : List.of(ObjetivoDeportivo.MANTENER, ObjetivoDeportivo.RENDIMIENTO,
                ObjetivoDeportivo.COMPETICION)) {
            assertThat(detector.detectar(datos(dias(), subiendo), objetivo)).as(objetivo.name()).isEmpty();
        }
        assertThat(detector.detectar(datos(dias(), subiendo), null)).isEmpty();
        // Todos el mismo día: no hay pendiente
        List<Pesaje> mismoDia = List.of(new Pesaje(INICIO, new BigDecimal("70")), new Pesaje(INICIO,
                new BigDecimal("71")), new Pesaje(INICIO, new BigDecimal("72")), new Pesaje(INICIO,
                new BigDecimal("73")));
        assertThat(detector.detectar(datos(dias(), mismoDia), ObjetivoDeportivo.PERDER_GRASA)).isEmpty();
    }

    @Test
    void variosPatronesSalenEnElOrdenDeLaTabla() {
        List<DiaDatos> dias = dias(dia -> dia.fecha().getDayOfWeek() == DayOfWeek.SUNDAY
                ? conComidas(dia, gramos(150)) : dia);
        for (int i : new int[] {21, 22, 23, 24}) {
            dias.set(i, conProteina(dias.get(i), 100));
        }
        List<Hallazgo> hallazgos = detector.detectar(datos(dias, pesajes("70.0", "70.2", "70.5", "70.7")),
                ObjetivoDeportivo.PERDER_GRASA);
        assertThat(tipos(hallazgos)).containsExactly(TipoInsight.PROTEINA_BAJA, TipoInsight.DIAS_SIN_REGISTRO,
                TipoInsight.TENDENCIA_PESO);
    }

    // ---------------------------------------------------------------------------------------------
    // Datos sintéticos
    // ---------------------------------------------------------------------------------------------

    /** Los 28 días perfectos. */
    private static List<DiaDatos> dias() {
        return dias(dia -> dia);
    }

    /** Los 28 días perfectos con un cambio en cada uno. */
    private static List<DiaDatos> dias(UnaryOperator<DiaDatos> cambio) {
        return dias(cambio, DIAS_ENTRENO);
    }

    private static List<DiaDatos> dias(UnaryOperator<DiaDatos> cambio, Set<DayOfWeek> diasEntreno) {
        List<DiaDatos> dias = new ArrayList<>();
        for (int i = 0; i < DIAS; i++) {
            LocalDate fecha = INICIO.plusDays(i);
            dias.add(cambio.apply(new DiaDatos(fecha, OBJETIVO, OBJETIVO,
                    diasEntreno.contains(fecha.getDayOfWeek()), COMIDAS_PERFECTAS)));
        }
        return dias;
    }

    /** Lunes, miércoles y viernes con esos hidratos; el resto perfecto. */
    private static List<DiaDatos> entrenoAl(int hidratos) {
        return dias(dia -> dia.diaEntreno() ? conHidratos(dia, hidratos) : dia);
    }

    private static DatosPeriodo datos(List<DiaDatos> dias, List<Pesaje> pesajes) {
        return new DatosPeriodo(INICIO, INICIO.plusDays(DIAS - 1), dias, pesajes);
    }

    /** Con esa proteína total (las otras cifras iguales), repartida en las 4 comidas sin llegar a 25 g en 3. */
    private static DiaDatos conProteina(DiaDatos dia, int proteina) {
        Nutrientes c = dia.consumido();
        BigDecimal grandes = BigDecimal.valueOf(proteina - 30L).divide(BigDecimal.TWO);
        return new DiaDatos(dia.fecha(), new Nutrientes(c.kcal(), BigDecimal.valueOf(proteina), c.carbohidratos(),
                c.grasa()), dia.objetivo(), dia.diaEntreno(),
                List.of(grandes, grandes, new BigDecimal("15"), new BigDecimal("15")));
    }

    private static DiaDatos conHidratos(DiaDatos dia, int hidratos) {
        Nutrientes c = dia.consumido();
        return new DiaDatos(dia.fecha(), new Nutrientes(c.kcal(), c.proteina(), BigDecimal.valueOf(hidratos),
                c.grasa()), dia.objetivo(), dia.diaEntreno(), dia.proteinaPorComida());
    }

    private static DiaDatos entrenoConHidratos(DiaDatos dia, int hidratos) {
        DiaDatos conEsosHidratos = conHidratos(dia, hidratos);
        return new DiaDatos(dia.fecha(), conEsosHidratos.consumido(), dia.objetivo(), true,
                dia.proteinaPorComida());
    }

    private static DiaDatos conComidas(DiaDatos dia, List<BigDecimal> proteinaPorComida) {
        return new DiaDatos(dia.fecha(), dia.consumido(), dia.objetivo(), dia.diaEntreno(), proteinaPorComida);
    }

    private static DiaDatos sinRegistro(DiaDatos dia) {
        return new DiaDatos(dia.fecha(), Nutrientes.CERO, dia.objetivo(), dia.diaEntreno(), List.of());
    }

    /** Un pesaje por semana desde el primer día. */
    private static List<Pesaje> pesajes(String... pesos) {
        List<Pesaje> pesajes = new ArrayList<>();
        for (int i = 0; i < pesos.length; i++) {
            pesajes.add(new Pesaje(INICIO.plusWeeks(i), new BigDecimal(pesos[i])));
        }
        return pesajes;
    }

    /** Bajando: va con PERDER_GRASA. */
    private static List<Pesaje> pesajesBajando() {
        return pesajes("71.0", "70.6", "70.3", "69.9");
    }

    private static List<BigDecimal> gramos(int... valores) {
        return Arrays.stream(valores).mapToObj(BigDecimal::valueOf).toList();
    }

    private static Nutrientes nutrientes(String kcal, String proteina, String carbohidratos, String grasa) {
        return new Nutrientes(new BigDecimal(kcal), new BigDecimal(proteina), new BigDecimal(carbohidratos),
                new BigDecimal(grasa));
    }

    private static Hallazgo unico(List<Hallazgo> hallazgos) {
        assertThat(hallazgos).hasSize(1);
        return hallazgos.get(0);
    }

    private static List<TipoInsight> tipos(List<Hallazgo> hallazgos) {
        return hallazgos.stream().map(Hallazgo::tipo).toList();
    }

    private static Optional<Hallazgo> buscar(List<Hallazgo> hallazgos, TipoInsight tipo) {
        return hallazgos.stream().filter(hallazgo -> hallazgo.tipo() == tipo).findFirst();
    }
}
