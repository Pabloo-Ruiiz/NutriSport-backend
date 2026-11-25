package com.nutrisport.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.menus.recomendacion.ContextoGeneracion;
import com.nutrisport.menus.recomendacion.MotorRecomendacion;
import com.nutrisport.menus.recomendacion.MotorRecomendacion.ResultadoDia;
import com.nutrisport.menus.recomendacion.PuntuadorRecetas;
import com.nutrisport.menus.recomendacion.RepartoFranjas;
import com.nutrisport.menus.recomendacion.SeleccionFranja;
import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Motor de recomendación de menús (§4.5) con 40 recetas sintéticas, sin Spring ni base de datos. */
class MotorRecomendacionTest {

    private static final List<TipoComida> FRANJAS = List.of(TipoComida.DESAYUNO, TipoComida.COMIDA,
            TipoComida.MERIENDA, TipoComida.CENA);
    /** Objetivo del día: 2300 kcal con macros coherentes (140·4 + 280·4 + 70·9 ≈ 2300). */
    private static final Nutrientes OBJETIVO = nutrientes("2300", "140", "280", "70");
    private static final RestriccionesUsuario SIN_GLUTEN_VEGANA = new RestriccionesUsuario(Set.of("GLUTEN"), false,
            Set.of(Dieta.VEGANA), Set.of(), Set.of());

    private final MotorRecomendacion motor = new MotorRecomendacion();

    private Alimento harina;
    private Alimento pollo;
    private Alimento garbanzos;
    private Alimento avenaSinRevisar;
    private List<Receta> recetas;

    @BeforeEach
    void preparar() {
        harina = alimento("Harina de trigo", List.of("GLUTEN"), true, true);
        pollo = alimento("Pechuga de pollo", List.of(), false, true);
        garbanzos = alimento("Garbanzos cocidos", List.of(), true, true);
        avenaSinRevisar = alimento("Copos de avena", List.of(), true, false);
        recetas = sinteticas();
    }

    // ---------------------------------------------------------------------------------------------
    // Una semana con restricción GLUTEN + VEGANA
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasRecetasSinteticasSonVariadas() {
        assertThat(recetas).hasSize(40);
        assertThat(recetas).filteredOn(r -> r.getAlergenosContiene().contains("GLUTEN")).hasSizeGreaterThan(10);
        assertThat(recetas).filteredOn(r -> !r.isAptoVegano()).hasSizeGreaterThan(10);
        // Hay recetas compatibles en todas las franjas, y no todas lo son
        ContextoGeneracion contexto = contexto(Set.of(), null);
        for (TipoComida franja : FRANJAS) {
            assertThat(recetas).filteredOn(r -> MotorRecomendacion.esCandidata(r, contexto, franja))
                    .as("candidatas para %s", franja).hasSizeGreaterThanOrEqualTo(3);
        }
        assertThat(recetas).filteredOn(r -> !MotorRecomendacion.esCompatible(r, contexto)).hasSizeGreaterThan(20);
    }

    @Test
    void sieteDiasSeguidosRespetanLasRestriccionesYElObjetivo() {
        ContextoGeneracion contexto = contexto(Set.of(), null);
        List<ResultadoDia> semana = semana(contexto, Map.of());

        assertThat(semana).hasSize(7);
        for (int dia = 0; dia < semana.size(); dia++) {
            ResultadoDia resultado = semana.get(dia);
            // Una receta por franja, en el orden del reparto
            assertThat(resultado.selecciones()).extracting(SeleccionFranja::tipoComida).as("día %d", dia)
                    .containsExactlyElementsOf(FRANJAS);
            // Ninguna receta incompatible: sin gluten (ni trazas), vegana y con alérgenos verificados
            for (SeleccionFranja seleccion : resultado.selecciones()) {
                assertThat(compatibleSinGlutenYVegana(seleccion.receta()))
                        .as("día %d, %s: %s", dia, seleccion.tipoComida(), seleccion.receta().getNombre()).isTrue();
                assertThat(seleccion.receta().getFranjasAptas()).contains(seleccion.tipoComida().name());
                assertThat(PuntuadorRecetas.RACIONES).contains(seleccion.raciones());
            }
            // En ±10 % de 2300 kcal o con aviso; y si cuadra, sin aviso
            boolean dentro = resultado.total().kcal().subtract(OBJETIVO.kcal()).abs()
                    .compareTo(new BigDecimal("230")) <= 0;
            assertThat(dentro || !resultado.avisos().isEmpty())
                    .as("día %d: %s kcal sin aviso", dia, resultado.total().kcal()).isTrue();
            if (MotorRecomendacion.dentroDeTolerancia(resultado.total(), OBJETIVO)) {
                assertThat(resultado.avisos()).as("día %d", dia).isEmpty();
            }
            // El total es la suma de las selecciones
            assertThat(resultado.total().kcal()).isEqualByComparingTo(resultado.selecciones().stream()
                    .map(SeleccionFranja::nutrientes).reduce(Nutrientes.CERO, Nutrientes::sumar).redondear().kcal());
        }
        // Con estas recetas el motor consigue cuadrar la semana
        assertThat(semana).allSatisfy(dia -> assertThat(dia.avisos()).isEmpty());
    }

    @Test
    void ningunaRecetaSeRepiteEnDosDiasSeguidosEnLaMismaFranja() {
        List<ResultadoDia> semana = semana(contexto(Set.of(), null), Map.of());

        for (int dia = 1; dia < semana.size(); dia++) {
            Map<TipoComida, Receta> ayer = porFranja(semana.get(dia - 1));
            Map<TipoComida, Receta> hoy = porFranja(semana.get(dia));
            for (TipoComida franja : FRANJAS) {
                assertThat(hoy.get(franja)).as("día %d, %s", dia, franja).isNotEqualTo(ayer.get(franja));
            }
            // Y dentro de un mismo día no se repite receta
            assertThat(new HashSet<>(hoy.values())).hasSize(hoy.size());
        }
    }

    @Test
    void unaComidaBloqueadaSeMantieneYSuEnergiaSeDescuenta() {
        Receta fija = recetas.stream()
                .filter(r -> MotorRecomendacion.esCandidata(r, contexto(Set.of(), null), TipoComida.DESAYUNO))
                .findFirst().orElseThrow();
        SeleccionFranja bloqueada = new SeleccionFranja(TipoComida.DESAYUNO, fija, new BigDecimal("1.5"), true);

        List<ResultadoDia> semana = semana(contexto(Set.of(), null), Map.of(3, List.of(bloqueada)));

        ResultadoDia dia = semana.get(3);
        List<SeleccionFranja> desayunos = dia.selecciones().stream()
                .filter(s -> s.tipoComida() == TipoComida.DESAYUNO).toList();
        assertThat(desayunos).containsExactly(bloqueada);
        assertThat(dia.selecciones()).filteredOn(SeleccionFranja::bloqueada).containsExactly(bloqueada);
        // El resto del día se ajusta a lo que queda: el total sigue cerca del objetivo
        assertThat(MotorRecomendacion.dentroDeTolerancia(dia.total(), OBJETIVO) || !dia.avisos().isEmpty()).isTrue();
        assertThat(dia.selecciones()).extracting(SeleccionFranja::receta).doesNotHaveDuplicates();
    }

    @Test
    void esDeterminista() {
        List<ResultadoDia> primera = semana(contexto(Set.of(), null), Map.of());
        List<ResultadoDia> segunda = semana(contexto(Set.of(), null), Map.of());

        assertThat(segunda).isEqualTo(primera);
    }

    // ---------------------------------------------------------------------------------------------
    // Reglas sueltas
    // ---------------------------------------------------------------------------------------------

    @Test
    void elRepartoDeFranjasSigueLaTabla() {
        assertThat(RepartoFranjas.de(3)).containsExactly(Map.entry(TipoComida.DESAYUNO, pct(25)),
                Map.entry(TipoComida.COMIDA, pct(40)), Map.entry(TipoComida.CENA, pct(35)));
        assertThat(RepartoFranjas.de(4)).containsExactly(Map.entry(TipoComida.DESAYUNO, pct(25)),
                Map.entry(TipoComida.COMIDA, pct(35)), Map.entry(TipoComida.MERIENDA, pct(15)),
                Map.entry(TipoComida.CENA, pct(25)));
        assertThat(RepartoFranjas.de(5)).containsExactly(Map.entry(TipoComida.DESAYUNO, pct(20)),
                Map.entry(TipoComida.MEDIA_MANANA, pct(10)), Map.entry(TipoComida.COMIDA, pct(30)),
                Map.entry(TipoComida.MERIENDA, pct(15)), Map.entry(TipoComida.CENA, pct(25)));
        // 6 = 5 + POST_ENTRENO 10 restado a la comida
        LinkedHashMap<TipoComida, BigDecimal> seis = RepartoFranjas.de(6);
        assertThat(seis).containsEntry(TipoComida.POST_ENTRENO, pct(10)).containsEntry(TipoComida.COMIDA, pct(20))
                .containsEntry(TipoComida.DESAYUNO, pct(20)).containsEntry(TipoComida.MEDIA_MANANA, pct(10))
                .containsEntry(TipoComida.MERIENDA, pct(15)).containsEntry(TipoComida.CENA, pct(25)).hasSize(6);
        for (int comidas = 3; comidas <= 6; comidas++) {
            assertThat(RepartoFranjas.de(comidas).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("100");
        }
        assertThatThrownBy(() -> RepartoFranjas.de(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepartoFranjas.de(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laPuntuacionSigueLaFormulaYSusBonificaciones() {
        Receta receta = receta(99, "Tofu con arroz", List.of(garbanzos), List.of("COMIDA", "MERIENDA"),
                nutrientes("500", "30", "60", "15"));
        Nutrientes exacto = nutrientes("500", "30", "60", "15");
        ContextoGeneracion sinFavoritas = contexto(Set.of(), null);

        // Ajuste perfecto: 0
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(), false,
                TipoComida.COMIDA)).isCloseTo(0, within(1e-9));
        // +20 % de todo: 0,5·20 + 0,3·20 + 0,1·20 + 0,1·20 = 20
        assertThat(PuntuadorRecetas.puntuar(receta, new BigDecimal("1.2"), exacto, sinFavoritas, Set.of(), false,
                TipoComida.COMIDA)).isCloseTo(20, within(1e-9));
        // Solo kcal −10 %, proteína +50 %: 0,5·10 + 0,3·50 = 20
        assertThat(PuntuadorRecetas.ajuste(nutrientes("450", "45", "60", "15"), exacto)).isCloseTo(20, within(1e-9));
        // Favorita −15; usada en los 2 días anteriores +25
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, contexto(Set.of(receta.getId()), null),
                Set.of(), false, TipoComida.COMIDA)).isCloseTo(-15, within(1e-9));
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(receta.getId()),
                false, TipoComida.COMIDA)).isCloseTo(25, within(1e-9));
        // pre-entreno: −10 solo en la MERIENDA de un día de entreno
        receta.setEtiquetas(List.of("pre-entreno"));
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(), true,
                TipoComida.MERIENDA)).isCloseTo(-10, within(1e-9));
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(), true,
                TipoComida.COMIDA)).isCloseTo(0, within(1e-9));
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(), false,
                TipoComida.MERIENDA)).isCloseTo(0, within(1e-9));
        // post-entreno: −10 en un día de entreno
        receta.setEtiquetas(List.of("post-entreno"));
        assertThat(PuntuadorRecetas.puntuar(receta, BigDecimal.ONE, exacto, sinFavoritas, Set.of(), true,
                TipoComida.COMIDA)).isCloseTo(-10, within(1e-9));
    }

    @Test
    void lasRacionesSonLasQueMejorAjustan() {
        Receta receta = receta(98, "Lentejas", List.of(garbanzos), List.of("COMIDA"), nutrientes("400", "24", "50", "10"));

        assertThat(PuntuadorRecetas.mejoresRaciones(receta, nutrientes("800", "48", "100", "20"))).isEqualByComparingTo("2");
        assertThat(PuntuadorRecetas.mejoresRaciones(receta, nutrientes("300", "18", "37.5", "7.5"))).isEqualByComparingTo("0.75");
        assertThat(PuntuadorRecetas.mejoresRaciones(receta, nutrientes("560", "33", "70", "14"))).isEqualByComparingTo("1.5");
    }

    @Test
    void elFiltroDuroDescartaLoQueNoCumple() {
        ContextoGeneracion contexto = contexto(Set.of(), 30);
        Receta valida = receta(97, "Hummus", List.of(garbanzos), List.of("MERIENDA"), nutrientes("300", "12", "30", "14"));
        valida.setTiempoPreparacionMin((short) 30);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isTrue();
        // Franja no apta
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.CENA)).isFalse();
        // Tiempo por encima del máximo (sin tiempo conocido sí entra)
        valida.setTiempoPreparacionMin((short) 31);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isFalse();
        valida.setTiempoPreparacionMin(null);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isTrue();
        // Sin publicar
        valida.setPublicada(false);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isFalse();
        valida.setPublicada(true);
        // Cálculo INCOMPLETO
        valida.setEstadoNutricional(EstadoNutricional.INCOMPLETO);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isFalse();
        valida.setEstadoNutricional(EstadoNutricional.APROXIMADO);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isTrue();
        // Con alergias, los alérgenos tienen que estar verificados
        valida.setAlergenosVerificados(false);
        assertThat(MotorRecomendacion.esCandidata(valida, contexto, TipoComida.MERIENDA)).isFalse();
        assertThat(MotorRecomendacion.esCandidata(valida,
                new ContextoGeneracion(RestriccionesUsuario.NINGUNA, Set.of(), null, 4), TipoComida.MERIENDA)).isTrue();
    }

    @Test
    void sinRecetasAptasParaUnaFranjaLaDejaVaciaConAviso() {
        List<Receta> soloComidas = recetas.stream()
                .filter(r -> !r.getFranjasAptas().contains("MERIENDA") && !r.getFranjasAptas().contains("DESAYUNO"))
                .toList();

        ResultadoDia dia = motor.generarDia(contexto(Set.of(), null), OBJETIVO, false, soloComidas, Set.of(), List.of());

        assertThat(dia.selecciones()).extracting(SeleccionFranja::tipoComida)
                .containsExactly(TipoComida.COMIDA, TipoComida.CENA);
        assertThat(dia.avisos()).anyMatch(aviso -> aviso.contains("el desayuno"))
                .anyMatch(aviso -> aviso.contains("la merienda"));
        assertThat(MotorRecomendacion.hayCandidatas(contexto(Set.of(), null), soloComidas)).isTrue();
        assertThat(MotorRecomendacion.hayCandidatas(contexto(Set.of(), null), List.of())).isFalse();
    }

    // ---------------------------------------------------------------------------------------------

    /** 7 días seguidos como el generador: las recetas de los 2 días anteriores penalizan. */
    private List<ResultadoDia> semana(ContextoGeneracion contexto, Map<Integer, List<SeleccionFranja>> bloqueadas) {
        List<ResultadoDia> dias = new ArrayList<>();
        List<Set<UUID>> usadas = new ArrayList<>();
        for (int dia = 0; dia < 7; dia++) {
            Set<UUID> recientes = new HashSet<>();
            usadas.subList(Math.max(0, usadas.size() - 2), usadas.size()).forEach(recientes::addAll);
            ResultadoDia resultado = motor.generarDia(contexto, OBJETIVO, dia % 2 == 0, recetas, recientes,
                    bloqueadas.getOrDefault(dia, List.of()));
            dias.add(resultado);
            usadas.add(resultado.selecciones().stream().map(s -> s.receta().getId()).collect(Collectors.toSet()));
        }
        return dias;
    }

    private ContextoGeneracion contexto(Set<UUID> favoritas, Integer tiempoMax) {
        return new ContextoGeneracion(SIN_GLUTEN_VEGANA, favoritas, tiempoMax, 4);
    }

    private static Map<TipoComida, Receta> porFranja(ResultadoDia dia) {
        return dia.selecciones().stream().collect(Collectors.toMap(SeleccionFranja::tipoComida, SeleccionFranja::receta));
    }

    /** Comprobación independiente del motor: ni gluten ni trazas, vegana y alérgenos verificados. */
    private static boolean compatibleSinGlutenYVegana(Receta receta) {
        return !receta.getAlergenosContiene().contains("GLUTEN") && !receta.getAlergenosTrazas().contains("GLUTEN")
                && receta.isAptoVegano() && receta.isAlergenosVerificados()
                && receta.getIngredientes().stream().map(RecetaIngrediente::getAlimento)
                        .allMatch(a -> a.isAptoVegano() && !a.getAlergenosContiene().contains("GLUTEN"));
    }

    /**
     * 40 recetas: la franja va por turnos (las de comida y cena valen para las dos); con gluten si i % 5 ∈ {0, 1}, no
     * veganas si i % 3 == 0 y, en la 2, alérgenos sin verificar. Las kcal por ración dependen de la franja.
     */
    private List<Receta> sinteticas() {
        List<Receta> lista = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            TipoComida franja = FRANJAS.get(i % 4);
            boolean gluten = i % 5 == 0 || i % 5 == 1;
            boolean noVegana = i % 3 == 0;
            List<Alimento> ingredientes = new ArrayList<>();
            ingredientes.add(i == 2 ? avenaSinRevisar : garbanzos);
            if (gluten) {
                ingredientes.add(harina);
            }
            if (noVegana) {
                ingredientes.add(pollo);
            }
            List<String> franjas = switch (franja) {
                case COMIDA, CENA -> List.of("COMIDA", "CENA");
                default -> List.of(franja.name());
            };
            BigDecimal base = switch (franja) {
                case DESAYUNO -> new BigDecimal("560");
                case COMIDA -> new BigDecimal("720");
                case MERIENDA -> new BigDecimal("330");
                default -> new BigDecimal("620");
            };
            BigDecimal kcal = base.add(BigDecimal.valueOf((i % 3 - 1) * 30L));
            Nutrientes porRacion = new Nutrientes(kcal,
                    kcal.multiply(new BigDecimal("0.061")).add(BigDecimal.valueOf(i % 2 * 3L)),
                    kcal.multiply(new BigDecimal("0.12")), kcal.multiply(new BigDecimal("0.03")))
                    .redondear();
            Receta receta = receta(i, "Receta " + (char) ('A' + i % 26) + i, ingredientes, franjas, porRacion);
            if (i == 2) {
                receta.setAlergenosVerificados(false);
            }
            lista.add(receta);
        }
        return lista;
    }

    private static Receta receta(int numero, String nombre, List<Alimento> ingredientes, List<String> franjas,
            Nutrientes porRacion) {
        Receta receta = new Receta();
        ReflectionTestUtils.setField(receta, "id", new UUID(0x1000L, numero));
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setFranjasAptas(new ArrayList<>(franjas));
        receta.setEstadoNutricional(EstadoNutricional.COMPLETO);
        receta.setKcalRacion(porRacion.kcal());
        receta.setProteinaRacion(porRacion.proteina());
        receta.setCarbohidratosRacion(porRacion.carbohidratos());
        receta.setGrasaRacion(porRacion.grasa());
        boolean vegana = true;
        Set<String> alergenos = new HashSet<>();
        boolean revisados = true;
        for (Alimento alimento : ingredientes) {
            RecetaIngrediente ingrediente = new RecetaIngrediente(alimento.getNombre(), "100 g");
            ingrediente.setAlimento(alimento);
            ingrediente.setCantidadGramos(new BigDecimal("100"));
            receta.anadirIngrediente(ingrediente);
            vegana &= alimento.isAptoVegano();
            alergenos.addAll(alimento.getAlergenosContiene());
            revisados &= alimento.isAlergenosRevisados();
        }
        receta.setAlergenosContiene(new ArrayList<>(alergenos));
        receta.setAptoVegano(vegana);
        receta.setAptoVegetariano(vegana);
        receta.setAptoPescetariano(vegana);
        receta.setAlergenosVerificados(revisados);
        return receta;
    }

    private static Alimento alimento(String nombre, List<String> alergenos, boolean vegano, boolean revisados) {
        Alimento alimento = new Alimento();
        ReflectionTestUtils.setField(alimento, "id", UUID.nameUUIDFromBytes(nombre.getBytes()));
        alimento.setNombre(nombre);
        alimento.setAlergenosContiene(alergenos);
        alimento.setAlergenosTrazas(List.of());
        alimento.setAlergenosRevisados(revisados);
        alimento.setAptoVegano(vegano);
        alimento.setAptoVegetariano(vegano);
        alimento.setAptoPescetariano(vegano);
        return alimento;
    }

    private static Nutrientes nutrientes(String kcal, String proteina, String carbohidratos, String grasa) {
        return new Nutrientes(new BigDecimal(kcal), new BigDecimal(proteina), new BigDecimal(carbohidratos),
                new BigDecimal(grasa));
    }

    private static BigDecimal pct(int valor) {
        return BigDecimal.valueOf(valor);
    }
}
