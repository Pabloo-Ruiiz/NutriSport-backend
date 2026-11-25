package com.nutrisport.seguimiento.servicio;

import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio.TotalesDia;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio.TotalesTipoComida;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.perfil.entidad.ObjetivoDiario;
import com.nutrisport.perfil.repositorio.ObjetivoDiarioRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.seguimiento.dto.AlimentoFrecuenteResponse;
import com.nutrisport.seguimiento.dto.EstadisticasResponse;
import com.nutrisport.seguimiento.dto.PuntoSerieResponse;
import com.nutrisport.seguimiento.entidad.Agrupacion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estadísticas del usuario autenticado en un rango de días (§3.F y §7.7, Ampliación):
 * <ul>
 * <li>Las medias se calculan sobre los días con al menos una comida registrada (un día sin registros no es un día de
 * ayuno, es un día sin datos).</li>
 * <li>El objetivo de cada día es el ObjetivoDiario guardado de ese día; si un día con registros no lo tiene, se crea
 * con el perfil actual (sin perfil no se crea y el día va sin objetivo).</li>
 * <li>Un día está en objetivo si tiene registros y |kcal consumidas − kcal objetivo| ≤ 10 % del objetivo.</li>
 * </ul>
 * No es de solo lectura: puede guardar objetivos. El rango tiene como mucho 366 días.
 */
@Service
@Transactional
public class EstadisticasServicio {

    static final int MAXIMO_DIAS = 366;
    static final BigDecimal TOLERANCIA_OBJETIVO = new BigDecimal("0.10");
    static final int TAMANO_TOP = 10;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal KCAL_G_PROTEINA = new BigDecimal("4");
    private static final BigDecimal KCAL_G_CARBOHIDRATOS = new BigDecimal("4");
    private static final BigDecimal KCAL_G_GRASA = new BigDecimal("9");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    /**
     * Datos de un día del rango.
     *
     * @param consumido suma de sus comidas (cero si no hay)
     * @param objetivo  su ObjetivoDiario guardado; nulo si no tiene
     * @param registros número de comidas registradas
     */
    record Dia(LocalDate fecha, Nutrientes consumido, Nutrientes objetivo, int registros) {

        boolean conRegistro() {
            return registros > 0;
        }

        /** Con registros y la energía en ±10 % del objetivo. */
        boolean enObjetivo() {
            if (!conRegistro() || objetivo == null || objetivo.kcal().signum() <= 0) {
                return false;
            }
            BigDecimal margen = objetivo.kcal().multiply(TOLERANCIA_OBJETIVO);
            return consumido.kcal().subtract(objetivo.kcal()).abs().compareTo(margen) <= 0;
        }
    }

    private final RegistroComidaRepositorio registroRepositorio;
    private final ObjetivoDiarioRepositorio objetivoRepositorio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final SeguridadUtils seguridadUtils;

    public EstadisticasServicio(RegistroComidaRepositorio registroRepositorio,
            ObjetivoDiarioRepositorio objetivoRepositorio, ObjetivoDiarioServicio objetivoDiarioServicio,
            SeguridadUtils seguridadUtils) {
        this.registroRepositorio = registroRepositorio;
        this.objetivoRepositorio = objetivoRepositorio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.seguridadUtils = seguridadUtils;
    }

    /**
     * @param agrupacion nula = DIA
     * @throws PeticionInvalidaException 400 si hasta es anterior a desde o el rango pasa de 366 días
     */
    public EstadisticasResponse calcular(LocalDate desde, LocalDate hasta, Agrupacion agrupacion) {
        validarRango(desde, hasta);
        return calcular(seguridadUtils.usuarioActualId(), desde, hasta,
                agrupacion == null ? Agrupacion.DIA : agrupacion);
    }

    EstadisticasResponse calcular(UUID usuarioId, LocalDate desde, LocalDate hasta, Agrupacion agrupacion) {
        List<Dia> dias = dias(usuarioId, desde, hasta);
        List<Dia> conRegistro = dias.stream().filter(Dia::conRegistro).toList();
        Nutrientes mediaDiaria = media(conRegistro.stream().map(Dia::consumido).toList());
        int diasEnObjetivo = (int) dias.stream().filter(Dia::enObjetivo).count();

        List<TotalesTipoComida> porTipo = registroRepositorio.totalesPorTipoComida(usuarioId, desde, hasta);
        Map<TipoComida, BigDecimal> kcalPorTipo = new EnumMap<>(TipoComida.class);
        if (!conRegistro.isEmpty()) {
            BigDecimal numeroDias = BigDecimal.valueOf(conRegistro.size());
            porTipo.forEach(tipo -> kcalPorTipo.put(tipo.getTipoComida(),
                    tipo.getKcal().divide(numeroDias, 1, RoundingMode.HALF_UP)));
        }

        BigDecimal kcalTotales = conRegistro.stream().map(dia -> dia.consumido().kcal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AlimentoFrecuenteResponse> top = registroRepositorio
                .alimentosMasConsumidos(usuarioId, desde, hasta, PageRequest.of(0, TAMANO_TOP)).stream()
                .map(alimento -> new AlimentoFrecuenteResponse(alimento.getAlimentoId(), alimento.getNombre(),
                        alimento.getVeces().intValue(), porcentaje(alimento.getKcal(), kcalTotales),
                        alimento.getFuente() == null ? null : alimento.getFuente().name()))
                .toList();

        return new EstadisticasResponse(desde, hasta, agrupacion, serie(dias, agrupacion), mediaDiaria,
                mediaObjetivo(dias), conRegistro.size(), diasEnObjetivo,
                conRegistro.isEmpty() ? 0 : diasEnObjetivo * 100 / conRegistro.size(),
                repartoMacros(mediaDiaria), kcalPorTipo, top);
    }

    /**
     * Todos los días del rango con lo consumido y su objetivo guardado; antes crea el objetivo de los días con
     * registros que no lo tengan (si hay perfil).
     */
    List<Dia> dias(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, TotalesDia> totales = registroRepositorio.totalesPorFecha(usuarioId, desde, hasta).stream()
                .collect(Collectors.toMap(TotalesDia::getFecha, Function.identity()));
        totales.keySet().forEach(fecha -> objetivoDiarioServicio.crearSiNoExiste(usuarioId, fecha));
        Map<LocalDate, Nutrientes> objetivos = objetivoRepositorio
                .findByUsuarioIdAndFechaBetweenOrderByFechaAsc(usuarioId, desde, hasta).stream()
                .collect(Collectors.toMap(ObjetivoDiario::getFecha, EstadisticasServicio::nutrientes));

        List<Dia> dias = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            TotalesDia total = totales.get(fecha);
            Nutrientes consumido = total == null ? Nutrientes.CERO
                    : new Nutrientes(total.getKcal(), total.getProteina(), total.getCarbohidratos(), total.getGrasa());
            dias.add(new Dia(fecha, consumido, objetivos.get(fecha),
                    total == null ? 0 : total.getRegistros().intValue()));
        }
        return dias;
    }

    /** 400 si hasta es anterior a desde o el rango (ambos incluidos) pasa de 366 días. */
    static void validarRango(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS,
                    List.of(new ErrorCampo("hasta", "Debe ser igual o posterior a desde")));
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS,
                    List.of(new ErrorCampo("hasta", "El rango no puede superar los " + MAXIMO_DIAS + " días")));
        }
    }

    /** Media de lo consumido; cero si la lista está vacía. Con un decimal. */
    static Nutrientes media(List<Nutrientes> valores) {
        if (valores.isEmpty()) {
            return Nutrientes.CERO;
        }
        BigDecimal numero = BigDecimal.valueOf(valores.size());
        Nutrientes suma = valores.stream().reduce(Nutrientes.CERO, Nutrientes::sumar);
        return new Nutrientes(suma.kcal().divide(numero, 1, RoundingMode.HALF_UP),
                suma.proteina().divide(numero, 1, RoundingMode.HALF_UP),
                suma.carbohidratos().divide(numero, 1, RoundingMode.HALF_UP),
                suma.grasa().divide(numero, 1, RoundingMode.HALF_UP));
    }

    /**
     * Media de los objetivos de los días con registro que lo tienen; si no hay, de todos los días con objetivo; nula
     * si ninguno lo tiene.
     */
    static Nutrientes mediaObjetivo(List<Dia> dias) {
        List<Nutrientes> deDiasConRegistro = dias.stream()
                .filter(dia -> dia.conRegistro() && dia.objetivo() != null).map(Dia::objetivo).toList();
        if (!deDiasConRegistro.isEmpty()) {
            return media(deDiasConRegistro);
        }
        List<Nutrientes> todos = dias.stream().map(Dia::objetivo).filter(objetivo -> objetivo != null).toList();
        return todos.isEmpty() ? null : media(todos);
    }

    // ---------------------------------------------------------------------------------------------

    private static List<PuntoSerieResponse> serie(List<Dia> dias, Agrupacion agrupacion) {
        Map<String, List<Dia>> periodos = dias.stream()
                .collect(Collectors.groupingBy(dia -> periodo(dia.fecha(), agrupacion), LinkedHashMap::new,
                        Collectors.toList()));
        return periodos.entrySet().stream()
                .map(periodo -> {
                    List<Dia> delPeriodo = periodo.getValue();
                    List<Dia> conRegistro = delPeriodo.stream().filter(Dia::conRegistro).toList();
                    return new PuntoSerieResponse(periodo.getKey(), delPeriodo.get(0).fecha(),
                            media(conRegistro.stream().map(Dia::consumido).toList()), mediaObjetivo(delPeriodo),
                            conRegistro.size(), (int) delPeriodo.stream().filter(Dia::enObjetivo).count());
                })
                .toList();
    }

    /** "2026-11-24", "2026-W48" (semana ISO) o "2026-11". */
    static String periodo(LocalDate fecha, Agrupacion agrupacion) {
        return switch (agrupacion) {
            case DIA -> fecha.toString();
            case SEMANA -> String.format("%d-W%02d", fecha.get(IsoFields.WEEK_BASED_YEAR),
                    fecha.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MES -> YearMonth.from(fecha).toString();
        };
    }

    /**
     * Porcentaje de la energía de cada macro (4, 4 y 9 kcal/g) redondeado por el método del resto mayor para que
     * sumen exactamente 100; todo 0 si no hay energía.
     */
    static Map<String, Integer> repartoMacros(Nutrientes media) {
        Map<String, BigDecimal> kcal = new LinkedHashMap<>();
        kcal.put("proteina", media.proteina().multiply(KCAL_G_PROTEINA));
        kcal.put("carbohidratos", media.carbohidratos().multiply(KCAL_G_CARBOHIDRATOS));
        kcal.put("grasa", media.grasa().multiply(KCAL_G_GRASA));
        BigDecimal total = kcal.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Integer> reparto = new LinkedHashMap<>();
        if (total.signum() <= 0) {
            kcal.keySet().forEach(macro -> reparto.put(macro, 0));
            return reparto;
        }
        Map<String, BigDecimal> exactos = new LinkedHashMap<>();
        kcal.forEach((macro, valor) -> exactos.put(macro,
                valor.multiply(CIEN).divide(total, 6, RoundingMode.HALF_UP)));
        exactos.forEach((macro, valor) -> reparto.put(macro, valor.intValue()));
        int faltan = 100 - reparto.values().stream().mapToInt(Integer::intValue).sum();
        exactos.entrySet().stream()
                .sorted((a, b) -> restoDecimal(b.getValue()).compareTo(restoDecimal(a.getValue())))
                .limit(faltan)
                .forEach(macro -> reparto.merge(macro.getKey(), 1, Integer::sum));
        return reparto;
    }

    private static BigDecimal restoDecimal(BigDecimal valor) {
        return valor.subtract(new BigDecimal(valor.toBigInteger()));
    }

    /** parte / total × 100 con un decimal; 0 si el total no es positivo. */
    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }

    /** Objetivo total del día (con los entrenamientos). */
    static Nutrientes nutrientes(ObjetivoDiario objetivo) {
        return new Nutrientes(objetivo.getKcalTotal(), objetivo.getProteinaG(), objetivo.getCarbohidratosTotalG(),
                objetivo.getGrasaG());
    }
}
