package com.nutrisport.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Intensidad;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.actividad.repositorio.TipoActividadRepositorio;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comidas.entidad.AlimentoRegistrado;
import com.nutrisport.comidas.entidad.OrigenRegistro;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.perfil.entidad.ObjetivoDiario;
import com.nutrisport.perfil.entidad.OrigenObjetivo;
import com.nutrisport.perfil.repositorio.ObjetivoDiarioRepositorio;
import com.nutrisport.seguimiento.dto.AlimentoFrecuenteResponse;
import com.nutrisport.seguimiento.dto.ComparativaResponse;
import com.nutrisport.seguimiento.dto.EstadisticasResponse;
import com.nutrisport.seguimiento.dto.PuntoSerieResponse;
import com.nutrisport.seguimiento.dto.RelacionNutricionResponse;
import com.nutrisport.seguimiento.entidad.Agrupacion;
import com.nutrisport.seguimiento.servicio.ComparativaServicio;
import com.nutrisport.seguimiento.servicio.EstadisticasServicio;
import com.nutrisport.seguimiento.servicio.RelacionNutricionActividadServicio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Estadísticas, comparativa y relación entreno/descanso (§3.F y §3.G) con 14 días de datos creados con los
 * repositorios: dos semanas ISO completas, del lunes 2 al domingo 15 de noviembre de 2026.
 * <p>
 * Las comidas llevan su instantánea nutricional fijada a mano y los objetivos se guardan directamente (el usuario no
 * tiene perfil, así que no se crea ninguno más):
 * <table>
 * <caption>Días del periodo</caption>
 * <tr><th>Día</th><th>Comidas</th><th>Consumido</th><th>Objetivo</th><th>En objetivo</th></tr>
 * <tr><td>Entreno (6: lun, mié, vie de cada semana)</td><td>avena + arroz + pollo</td><td>2300 kcal</td>
 * <td>2500 kcal</td><td>sí (−8 %)</td></tr>
 * <tr><td>Descanso, 1.ª semana (4)</td><td>avena + pollo</td><td>1100 kcal</td><td>2000 kcal</td>
 * <td>no (−45 %)</td></tr>
 * <tr><td>Descanso, 2.ª semana (4)</td><td>avena + pollo + yogur</td><td>2000 kcal</td><td>2000 kcal</td>
 * <td>sí</td></tr>
 * </table>
 */
class EstadisticasServicioTest extends IntegracionBase {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate DOMINGO = LUNES.plusDays(13);
    /** Lunes, miércoles y viernes de las dos semanas. */
    private static final Set<Integer> DIAS_ENTRENO = Set.of(0, 2, 4, 7, 9, 11);
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");
    private static final String PREFIJO_REFERENCIA = "TEST-ESTADISTICAS-";

    // kcal, proteína, hidratos, grasa de cada línea
    private static final Nutrientes AVENA = nutrientes("400", "14", "60", "8");
    private static final Nutrientes ARROZ = nutrientes("1200", "24", "260", "4");
    private static final Nutrientes POLLO = nutrientes("700", "100", "0", "30");
    private static final Nutrientes YOGUR = nutrientes("900", "30", "100", "40");

    @Autowired
    private EstadisticasServicio estadisticasServicio;
    @Autowired
    private RelacionNutricionActividadServicio relacionServicio;
    @Autowired
    private SeguridadUtils seguridadUtils;
    @Autowired
    private RegistroComidaRepositorio registroRepositorio;
    @Autowired
    private EntrenamientoRepositorio entrenamientoRepositorio;
    @Autowired
    private TipoActividadRepositorio tipoActividadRepositorio;
    @Autowired
    private ObjetivoDiarioRepositorio objetivoRepositorio;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private Usuario ana;
    private Alimento avena;
    private Alimento arroz;
    private Alimento pollo;
    private Alimento yogur;

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken().usuario();
        avena = alimento("Copos de avena", FuenteDato.BEDCA);
        arroz = alimento("Arroz blanco", FuenteDato.USDA);
        pollo = alimento("Pechuga de pollo", FuenteDato.BEDCA);
        yogur = alimento("Yogur griego", FuenteDato.OPEN_FOOD_FACTS);

        for (int i = 0; i < 14; i++) {
            LocalDate fecha = LUNES.plusDays(i);
            boolean entreno = DIAS_ENTRENO.contains(i);
            comida(ana, fecha, "08:00", TipoComida.DESAYUNO, avena, AVENA);
            if (entreno) {
                comida(ana, fecha, "14:00", TipoComida.COMIDA, arroz, ARROZ);
                entrenamiento(fecha, EstadoEntrenamiento.REALIZADO);
            } else if (i >= 7) {
                comida(ana, fecha, "17:00", TipoComida.MERIENDA, yogur, YOGUR);
            }
            // El entreno es de 18:00 a 19:00: la cena de las 19:30 entra en la ventana de 2 h, salvo la del viernes
            // 13 (a las 21:30)
            comida(ana, fecha, i == 11 ? "21:30" : "19:30", TipoComida.CENA, pollo, POLLO);
            objetivo(fecha, entreno);
        }
        // Un entrenamiento omitido no hace del domingo un día de entreno
        entrenamiento(DOMINGO, EstadoEntrenamiento.OMITIDO);
        // Las comidas de otro usuario no cuentan
        comida(crearUsuarioYToken().usuario(), LUNES, "10:00", TipoComida.MEDIA_MANANA, yogur, YOGUR);

        autenticarComo(ana);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE registro_comida, entrenamiento, objetivo_diario, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa LIKE ?", PREFIJO_REFERENCIA + "%");
    }

    @Test
    void laMediaDiariaElObjetivoMedioYLosDiasEnObjetivo() {
        EstadisticasResponse estadisticas = estadisticasServicio.calcular(LUNES, DOMINGO, Agrupacion.DIA);

        // (6 × 2300 + 4 × 1100 + 4 × 2000) / 14 = 1871,43 kcal; proteína 1860 / 14; hidratos 2800 / 14; grasa 716 / 14
        assertNutrientes(estadisticas.mediaDiaria(), "1871.4", "132.9", "200.0", "51.1");
        // (6 × 2500 + 8 × 2000) / 14
        assertThat(estadisticas.objetivoMedio().kcal()).isEqualByComparingTo("2214.3");
        assertThat(estadisticas.diasConRegistro()).isEqualTo(14);
        // Los 6 de entreno (−8 %) y los 4 de descanso de la 2.ª semana; no los de descanso de la 1.ª (−45 %)
        assertThat(estadisticas.diasEnObjetivo()).isEqualTo(10);
        assertThat(estadisticas.porcentajeAdherencia()).isEqualTo(71);

        // Un punto por día con lo consumido y su objetivo guardado
        assertThat(estadisticas.serie()).hasSize(14);
        PuntoSerieResponse lunes = estadisticas.serie().get(0);
        assertThat(lunes.periodo()).isEqualTo("2026-11-02");
        assertThat(lunes.consumido().kcal()).isEqualByComparingTo("2300");
        assertThat(lunes.objetivo().kcal()).isEqualByComparingTo("2500");
        assertThat(lunes.diasEnObjetivo()).isEqualTo(1);
        PuntoSerieResponse martes = estadisticas.serie().get(1);
        assertThat(martes.consumido().kcal()).isEqualByComparingTo("1100");
        assertThat(martes.objetivo().kcal()).isEqualByComparingTo("2000");
        assertThat(martes.diasEnObjetivo()).isZero();

        // Kcal medias al día por tipo de comida: suman la media diaria
        assertThat(estadisticas.kcalPorTipoComida())
                .containsOnlyKeys(TipoComida.DESAYUNO, TipoComida.COMIDA, TipoComida.MERIENDA, TipoComida.CENA);
        assertThat(estadisticas.kcalPorTipoComida().get(TipoComida.DESAYUNO)).isEqualByComparingTo("400.0");
        assertThat(estadisticas.kcalPorTipoComida().get(TipoComida.CENA)).isEqualByComparingTo("700.0");
        assertThat(estadisticas.kcalPorTipoComida().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("1871.4");
    }

    @Test
    void elRepartoDeMacrosSumaCien() {
        Map<String, Integer> reparto = estadisticasServicio.calcular(LUNES, DOMINGO, Agrupacion.DIA)
                .repartoMacrosPct();

        assertThat(reparto).containsOnlyKeys("proteina", "carbohidratos", "grasa");
        assertThat(reparto.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        // 132,9 × 4 = 531,6 · 200 × 4 = 800 · 51,1 × 9 = 459,9 kcal → 29,67 % · 44,66 % · 25,67 %
        assertThat(reparto).containsEntry("proteina", 30).containsEntry("carbohidratos", 44)
                .containsEntry("grasa", 26);

        // Sin registros, todo 0
        assertThat(estadisticasServicio.calcular(DOMINGO.plusDays(1), DOMINGO.plusDays(7), Agrupacion.DIA)
                .repartoMacrosPct().values()).containsOnly(0);
    }

    @Test
    void elTopDeAlimentosVaPorVecesYDespuesPorEnergia() {
        EstadisticasResponse estadisticas = estadisticasServicio.calcular(LUNES, DOMINGO, Agrupacion.DIA);

        // Pollo y avena 14 veces (el pollo aporta más), arroz 6 y yogur 4 (el yogur del otro usuario no cuenta)
        assertThat(estadisticas.top()).extracting(AlimentoFrecuenteResponse::nombre)
                .containsExactly("Pechuga de pollo", "Copos de avena", "Arroz blanco", "Yogur griego");
        assertThat(estadisticas.top()).extracting(AlimentoFrecuenteResponse::veces).containsExactly(14, 14, 6, 4);
        // 9800, 5600, 7200 y 3600 de 26 200 kcal
        assertThat(estadisticas.top()).extracting(AlimentoFrecuenteResponse::porcentajeEnergia)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("37.4"), new BigDecimal("21.4"), new BigDecimal("27.5"),
                        new BigDecimal("13.7"));
        assertThat(estadisticas.top()).extracting(AlimentoFrecuenteResponse::fuente)
                .containsExactly("BEDCA", "BEDCA", "USDA", "OPEN_FOOD_FACTS");
        assertThat(estadisticas.top().get(0).alimentoId()).isEqualTo(pollo.getId());
    }

    @Test
    void laAgrupacionPorSemanasDaUnPuntoPorSemanaIso() {
        EstadisticasResponse estadisticas = estadisticasServicio.calcular(LUNES, DOMINGO, Agrupacion.SEMANA);

        assertThat(estadisticas.serie()).hasSize(2);
        PuntoSerieResponse primera = estadisticas.serie().get(0);
        PuntoSerieResponse segunda = estadisticas.serie().get(1);
        assertThat(primera.periodo()).isEqualTo("2026-W45");
        assertThat(primera.desde()).isEqualTo(LUNES);
        assertThat(segunda.periodo()).isEqualTo("2026-W46");
        assertThat(segunda.desde()).isEqualTo(LUNES.plusDays(7));
        // (3 × 2300 + 4 × 1100) / 7 y (3 × 2300 + 4 × 2000) / 7
        assertThat(primera.consumido().kcal()).isEqualByComparingTo("1614.3");
        assertThat(segunda.consumido().kcal()).isEqualByComparingTo("2128.6");
        // (3 × 2500 + 4 × 2000) / 7
        assertThat(primera.objetivo().kcal()).isEqualByComparingTo("2214.3");
        assertThat(primera.diasConRegistro()).isEqualTo(7);
        assertThat(primera.diasEnObjetivo()).isEqualTo(3);
        assertThat(segunda.diasEnObjetivo()).isEqualTo(7);
        // Las medias del periodo no dependen de la agrupación
        assertThat(estadisticas.mediaDiaria().kcal()).isEqualByComparingTo("1871.4");

        // Por meses, un solo punto
        assertThat(estadisticasServicio.calcular(LUNES, DOMINGO, Agrupacion.MES).serie())
                .extracting(PuntoSerieResponse::periodo).containsExactly("2026-11");
    }

    @Test
    void laComparativaSemanalDaLasVariacionesRespectoALaSemanaAnterior() {
        // "Hoy" es el domingo 15: la semana actual es la 2.ª y la anterior, la 1.ª
        Clock domingo = Clock.fixed(DOMINGO.atTime(12, 0).atZone(MADRID).toInstant(), MADRID);
        ComparativaServicio comparativaServicio = new ComparativaServicio(estadisticasServicio, seguridadUtils,
                domingo);

        ComparativaResponse comparativa = comparativaServicio.comparar(Agrupacion.SEMANA);

        assertThat(comparativa.actual().desde()).isEqualTo(LUNES.plusDays(7));
        assertThat(comparativa.actual().hasta()).isEqualTo(DOMINGO);
        assertThat(comparativa.anterior().desde()).isEqualTo(LUNES);
        assertThat(comparativa.anterior().hasta()).isEqualTo(LUNES.plusDays(6));
        assertThat(comparativa.actual().mediaDiaria().kcal()).isEqualByComparingTo("2128.6");
        assertThat(comparativa.anterior().mediaDiaria().kcal()).isEqualByComparingTo("1614.3");
        // Resumidas: sin serie
        assertThat(comparativa.actual().serie()).isEmpty();
        assertThat(comparativa.anterior().serie()).isEmpty();

        // Medias de cada semana redondeadas a un decimal: kcal (2128,6 − 1614,3) / 1614,3; proteína 141,4 frente a
        // 124,3; hidratos 1600 / 7 = 228,6 frente a 1200 / 7 = 171,4
        assertThat(comparativa.variacionPct().get("kcal")).isEqualByComparingTo("31.9");
        assertThat(comparativa.variacionPct().get("proteina")).isEqualByComparingTo("13.8");
        assertThat(comparativa.variacionPct().get("carbohidratos")).isEqualByComparingTo("33.4");
        // Adherencia: 7 de 7 (100 %) frente a 3 de 7 (42 %)
        assertThat(comparativa.actual().porcentajeAdherencia()).isEqualTo(100);
        assertThat(comparativa.anterior().porcentajeAdherencia()).isEqualTo(42);
        assertThat(comparativa.variacionPct().get("adherenciaPts")).isEqualByComparingTo("58");

        // Por meses, el mes anterior (octubre) no tiene datos: no hay variación
        ComparativaResponse mensual = comparativaServicio.comparar(Agrupacion.MES);
        assertThat(mensual.actual().desde()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(mensual.anterior().desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(mensual.anterior().hasta()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(mensual.variacionPct().get("kcal")).isNull();

        assertThatThrownBy(() -> comparativaServicio.comparar(Agrupacion.DIA))
                .isInstanceOf(PeticionInvalidaException.class);
    }

    @Test
    void laRelacionEntrenoDescansoComparaLasMediasDeCadaTipoDeDia() {
        RelacionNutricionResponse relacion = relacionServicio.calcular(LUNES, DOMINGO);

        // El entrenamiento omitido del domingo no cuenta
        assertThat(relacion.diasEntreno()).isEqualTo(6);
        assertThat(relacion.diasDescanso()).isEqualTo(8);
        assertThat(relacion.sesiones()).isEqualTo(6);
        assertNutrientes(relacion.mediaEntreno(), "2300", "138", "320", "42");
        // (4 × 1100 + 4 × 2000) / 8; proteína (4 × 114 + 4 × 144) / 8
        assertNutrientes(relacion.mediaDescanso(), "1550", "129", "110", "58");
        assertThat(relacion.mediaEntreno().kcal()).isNotEqualByComparingTo(relacion.mediaDescanso().kcal());
        assertThat(relacion.objetivoEntreno().kcal()).isEqualByComparingTo("2500");
        assertThat(relacion.objetivoDescanso().kcal()).isEqualByComparingTo("2000");
        // La cena de las 19:30 (100 g de proteína) cae en la ventana de 2 h salvo la del viernes 13 (21:30)
        assertThat(relacion.sesionesConProteinaPost()).isEqualTo(5);
    }

    @Test
    void elRangoNoPuedeIrAlRevesNiPasarDe366Dias() {
        assertThatThrownBy(() -> estadisticasServicio.calcular(DOMINGO, LUNES, Agrupacion.DIA))
                .isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> estadisticasServicio.calcular(LUNES, LUNES.plusDays(366), Agrupacion.DIA))
                .isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> relacionServicio.calcular(LUNES, LUNES.plusDays(366)))
                .isInstanceOf(PeticionInvalidaException.class);
        // 366 días justos sí
        assertThat(estadisticasServicio.calcular(LUNES, LUNES.plusDays(365), Agrupacion.MES).diasConRegistro())
                .isEqualTo(14);
    }

    // ---------------------------------------------------------------------------------------------

    private static void assertNutrientes(Nutrientes nutrientes, String kcal, String proteina, String carbohidratos,
            String grasa) {
        assertThat(nutrientes.kcal()).as("kcal").isEqualByComparingTo(kcal);
        assertThat(nutrientes.proteina()).as("proteína").isEqualByComparingTo(proteina);
        assertThat(nutrientes.carbohidratos()).as("hidratos").isEqualByComparingTo(carbohidratos);
        assertThat(nutrientes.grasa()).as("grasa").isEqualByComparingTo(grasa);
    }

    private void comida(Usuario usuario, LocalDate fecha, String hora, TipoComida tipo, Alimento alimento,
            Nutrientes aporte) {
        RegistroComida registro = new RegistroComida();
        registro.setUsuario(usuario);
        registro.setFecha(fecha);
        registro.setHora(LocalTime.parse(hora));
        registro.setTipoComida(tipo);
        registro.setOrigen(OrigenRegistro.MANUAL);
        AlimentoRegistrado linea = new AlimentoRegistrado();
        linea.setAlimento(alimento);
        linea.setCantidad(new BigDecimal("100"));
        linea.setUnidad("g");
        linea.setGramos(new BigDecimal("100"));
        linea.setKcal(aporte.kcal());
        linea.setProteina(aporte.proteina());
        linea.setCarbohidratos(aporte.carbohidratos());
        linea.setGrasa(aporte.grasa());
        registro.anadirAlimento(linea);
        registro.recalcularTotales();
        registroRepositorio.save(registro);
    }

    private void entrenamiento(LocalDate fecha, EstadoEntrenamiento estado) {
        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setUsuario(ana);
        entrenamiento.setTipoActividad(tipoActividadRepositorio.getReferenceById("CARRERA_12KMH"));
        entrenamiento.setFechaHora(fecha.atTime(18, 0).atZone(MADRID).toOffsetDateTime());
        entrenamiento.setDuracionMin((short) 60);
        entrenamiento.setIntensidad(Intensidad.MEDIA);
        entrenamiento.setEstado(estado);
        entrenamiento.setKcalEstimadas(new BigDecimal("500"));
        entrenamientoRepositorio.save(entrenamiento);
    }

    /** 2000 kcal de base y, si es día de entreno, 500 más (con sus hidratos: +125 g). */
    private void objetivo(LocalDate fecha, boolean entreno) {
        ObjetivoDiario objetivo = new ObjetivoDiario();
        objetivo.setUsuario(ana);
        objetivo.setFecha(fecha);
        objetivo.setKcalBase(new BigDecimal("2000"));
        objetivo.setProteinaG(new BigDecimal("100"));
        objetivo.setCarbohidratosG(new BigDecimal("250"));
        objetivo.setGrasaG(new BigDecimal("70"));
        objetivo.setKcalEntreno(new BigDecimal(entreno ? "500" : "0"));
        objetivo.setKcalTotal(new BigDecimal(entreno ? "2500" : "2000"));
        objetivo.setCarbohidratosTotalG(new BigDecimal(entreno ? "375" : "250"));
        objetivo.setOrigen(OrigenObjetivo.CALCULADO);
        objetivoRepositorio.save(objetivo);
    }

    private Alimento alimento(String nombre, FuenteDato fuente) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.getReferenceById(CATEGORIA_OTROS));
        alimento.setKcal100(new BigDecimal("100"));
        alimento.setProteinas100(BigDecimal.ONE);
        alimento.setCarbohidratos100(BigDecimal.ONE);
        alimento.setGrasas100(BigDecimal.ONE);
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setFuente(fuente);
        alimento.setReferenciaExterna(PREFIJO_REFERENCIA + nombre);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    private static Nutrientes nutrientes(String kcal, String proteina, String carbohidratos, String grasa) {
        return new Nutrientes(new BigDecimal(kcal), new BigDecimal(proteina), new BigDecimal(carbohidratos),
                new BigDecimal(grasa));
    }

    private static void autenticarComo(Usuario usuario) {
        UsuarioAutenticado autenticado = UsuarioAutenticado.de(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(autenticado, null, autenticado.getAuthorities()));
    }
}
