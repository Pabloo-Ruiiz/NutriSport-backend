package com.nutrisport.asistente.herramientas;

import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.servicio.EntrenamientoServicio;
import com.nutrisport.comidas.dto.ComidasDiaResponse;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.servicio.ComidaServicio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.menus.dto.ComidaPendienteResponse;
import com.nutrisport.menus.dto.ComidaPlanificadaResponse;
import com.nutrisport.menus.servicio.MenuServicio;
import com.nutrisport.seguimiento.dto.InsightResponse;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse;
import com.nutrisport.seguimiento.servicio.InsightServicio;
import com.nutrisport.seguimiento.servicio.ResumenDiarioServicio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Herramientas de <b>solo lectura</b> que el asistente puede invocar para responder con los datos reales del usuario
 * (§3.H, §4.4 y §8.2). Ninguna modifica los datos del usuario; la única escritura posible es la misma que hace la
 * consulta del resumen diario (guardar el objetivo calculado de ese día si aún no existe).
 * <p>
 * Seguridad: el usuario sale <b>solo</b> del contexto de la herramienta ({@code toolContext.getContext().get(
 * "usuarioId")}), que rellena el backend con el usuario autenticado; ningún parámetro que pueda rellenar el modelo
 * identifica al usuario, así que un mensaje que pida datos de otro no puede conseguirlos. Los servicios se llaman con
 * el usuario explícito, sin depender del contexto de seguridad, porque Spring AI puede ejecutar las herramientas en
 * otro hilo.
 * <p>
 * Cada herramienta añade su nombre a la lista {@code "herramientasUsadas"} del contexto (si la hay) para guardarla con
 * la respuesta. Las respuestas son records simples, sin identificadores internos y con las cifras redondeadas a un
 * decimal.
 */
@Component
public class HerramientasAsistente {

    /** Clave del contexto con el UUID del usuario autenticado. */
    public static final String CLAVE_USUARIO = "usuarioId";
    /** Clave del contexto con la lista (mutable y segura entre hilos) de herramientas usadas. */
    public static final String CLAVE_HERRAMIENTAS = "herramientasUsadas";

    static final int MAXIMO_DIAS_COMIDAS = 14;
    static final int MAXIMO_DIAS_ENTRENAMIENTOS = 31;
    private static final String PERFIL_REQUERIDO = "PERFIL_REQUERIDO";

    // ---------------------------------------------------------------------------------------------
    // Respuestas
    // ---------------------------------------------------------------------------------------------

    /**
     * Resumen de un día.
     *
     * @param objetivo          objetivo total del día (con los entrenamientos); nulo si el usuario no tiene perfil
     * @param restante          objetivo − consumido por nutriente (nunca negativo); nulo sin objetivo
     * @param porcentajeKcal    kcal consumidas / objetivo × 100; nulo sin objetivo
     * @param pendientesDelPlan comidas del menú de ese día aún no registradas
     * @param aviso             explicación si falta algún dato (p. ej. sin perfil); nulo si no
     */
    public record ResumenDia(LocalDate fecha, Nutrientes consumido, Nutrientes objetivo, Nutrientes restante,
            Integer porcentajeKcal, BigDecimal kcalEntreno, int comidasRegistradas, int entrenamientos,
            List<Plato> pendientesDelPlan, String aviso) {
    }

    /** Comidas registradas entre dos fechas, solo los días con alguna. */
    public record Comidas(LocalDate desde, LocalDate hasta, List<DiaComidas> dias) {
    }

    public record DiaComidas(LocalDate fecha, Nutrientes totales, List<Comida> comidas) {
    }

    /** @param alimentos "nombre (gramos g)" */
    public record Comida(String tipoComida, LocalTime hora, Nutrientes totales, List<String> alimentos) {
    }

    /** Entrenamientos entre dos fechas (planificados, realizados u omitidos). */
    public record Entrenamientos(LocalDate desde, LocalDate hasta, List<Sesion> sesiones,
            BigDecimal kcalEstimadasQueCuentan) {
    }

    /** @param estado PLANIFICADO, REALIZADO u OMITIDO (los omitidos no suman gasto) */
    public record Sesion(LocalDate fecha, LocalTime hora, String actividad, int duracionMin, String intensidad,
            String estado, BigDecimal kcalEstimadas) {
    }

    /** Lo planificado para un día en los menús activos. */
    public record MenuDia(LocalDate fecha, List<Plato> platos, Nutrientes total) {
    }

    /** @param nutrientes los de las raciones planificadas; nulos si la receta no tiene cálculo */
    public record Plato(String tipoComida, String receta, BigDecimal raciones, Nutrientes nutrientes,
            boolean consumida) {
    }

    /** Patrones detectados en los últimos 28 días. */
    public record Tendencias(List<Tendencia> tendencias) {
    }

    public record Tendencia(String tipo, String severidad, String titulo, String texto, LocalDate desde,
            LocalDate hasta) {
    }

    // ---------------------------------------------------------------------------------------------

    private final ResumenDiarioServicio resumenDiarioServicio;
    private final ComidaServicio comidaServicio;
    private final EntrenamientoServicio entrenamientoServicio;
    private final MenuServicio menuServicio;
    private final InsightServicio insightServicio;
    private final Clock reloj;

    public HerramientasAsistente(ResumenDiarioServicio resumenDiarioServicio, ComidaServicio comidaServicio,
            EntrenamientoServicio entrenamientoServicio, MenuServicio menuServicio, InsightServicio insightServicio,
            Clock reloj) {
        this.resumenDiarioServicio = resumenDiarioServicio;
        this.comidaServicio = comidaServicio;
        this.entrenamientoServicio = entrenamientoServicio;
        this.menuServicio = menuServicio;
        this.insightServicio = insightServicio;
        this.reloj = reloj;
    }

    @Tool(name = "resumenDiario", description = "Resumen nutricional de un día del usuario: lo consumido (kcal, "
            + "proteína, hidratos y grasa en gramos) frente a su objetivo de ese día (que incluye el gasto de sus "
            + "entrenamientos), lo que le queda, cuántas comidas y entrenamientos registró y las comidas de su plan "
            + "aún pendientes. Úsala para cualquier pregunta sobre lo que comió o le falta un día concreto.")
    public ResumenDia resumenDiario(
            @ToolParam(description = "Día en formato AAAA-MM-DD; si se omite, hoy", required = false) LocalDate fecha,
            ToolContext contexto) {
        UUID usuarioId = usuarioId(contexto);
        registrarUso(contexto, "resumenDiario");
        LocalDate dia = fecha != null ? fecha : hoy();
        try {
            ResumenDiarioResponse resumen = resumenDiarioServicio.de(usuarioId, dia);
            return new ResumenDia(dia, resumen.consumido().redondear(), resumen.objetivo().redondear(),
                    resumen.restante().redondear(), resumen.porcentajes().kcal(),
                    resumen.kcalEntreno().setScale(1, RoundingMode.HALF_UP), resumen.comidas().size(),
                    resumen.entrenamientos().size(),
                    resumen.comidasPlanificadasPendientes().stream().map(HerramientasAsistente::plato).toList(),
                    null);
        } catch (ReglaNegocioException sinPerfil) {
            if (!PERFIL_REQUERIDO.equals(sinPerfil.getCodigo())) {
                throw sinPerfil;
            }
            // Sin perfil no hay objetivo, pero lo consumido sí se puede consultar
            ComidasDiaResponse comidas = comidaServicio.dia(usuarioId, dia);
            int entrenamientos = entrenamientoServicio.listar(usuarioId, dia, dia, null).size();
            return new ResumenDia(dia, comidas.totales().redondear(), null, null, null, null,
                    comidas.comidas().size(), entrenamientos, List.of(),
                    "El usuario no ha completado su perfil deportivo: no hay objetivo calculado");
        }
    }

    @Tool(name = "comidas", description = "Comidas registradas por el usuario entre dos fechas (ambas incluidas, "
            + "como mucho 14 días): por día, los totales y cada comida con su tipo, hora, nutrientes y alimentos con "
            + "sus gramos. Úsala para preguntas sobre qué comió o sus hábitos en varios días.")
    public Comidas comidas(
            @ToolParam(description = "Primer día, AAAA-MM-DD") LocalDate desde,
            @ToolParam(description = "Último día, AAAA-MM-DD (como mucho 13 días después de desde)") LocalDate hasta,
            ToolContext contexto) {
        UUID usuarioId = usuarioId(contexto);
        registrarUso(contexto, "comidas");
        validarRango(desde, hasta, MAXIMO_DIAS_COMIDAS);
        List<DiaComidas> dias = comidaServicio.listar(usuarioId, desde, hasta, null).stream()
                .map(dia -> new DiaComidas(dia.fecha(), dia.totales().redondear(),
                        dia.comidas().stream().map(HerramientasAsistente::comida).toList()))
                .toList();
        return new Comidas(desde, hasta, dias);
    }

    @Tool(name = "entrenamientos", description = "Entrenamientos del usuario entre dos fechas (ambas incluidas, como "
            + "mucho 31 días), planificados, realizados u omitidos: fecha, hora, actividad, duración, intensidad y "
            + "gasto neto estimado. Úsala para preguntas sobre su actividad o su carga de entrenamiento.")
    public Entrenamientos entrenamientos(
            @ToolParam(description = "Primer día, AAAA-MM-DD") LocalDate desde,
            @ToolParam(description = "Último día, AAAA-MM-DD (como mucho 30 días después de desde)") LocalDate hasta,
            ToolContext contexto) {
        UUID usuarioId = usuarioId(contexto);
        registrarUso(contexto, "entrenamientos");
        validarRango(desde, hasta, MAXIMO_DIAS_ENTRENAMIENTOS);
        ZoneId zona = reloj.getZone();
        List<EntrenamientoResponse> lista = entrenamientoServicio.listar(usuarioId, desde, hasta, null);
        List<Sesion> sesiones = lista.stream()
                .map(sesion -> {
                    var local = sesion.fechaHora().atZoneSameInstant(zona);
                    return new Sesion(local.toLocalDate(), local.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                            sesion.tipoActividadNombre(), sesion.duracionMin(), sesion.intensidad().name(),
                            sesion.estado().name(), sesion.kcalEstimadas().setScale(1, RoundingMode.HALF_UP));
                })
                .toList();
        BigDecimal queCuentan = lista.stream()
                .filter(sesion -> sesion.estado() != EstadoEntrenamiento.OMITIDO)
                .map(EntrenamientoResponse::kcalEstimadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
        return new Entrenamientos(desde, hasta, sesiones, queCuentan);
    }

    @Tool(name = "menuDelDia", description = "Lo que el usuario tiene planificado comer un día según sus menús "
            + "activos: cada comida con su tipo, receta, raciones, nutrientes y si ya la ha comido, y el total del "
            + "día. Úsala para preguntas como «¿qué ceno hoy según mi plan?».")
    public MenuDia menuDelDia(
            @ToolParam(description = "Día en formato AAAA-MM-DD; si se omite, hoy", required = false) LocalDate fecha,
            ToolContext contexto) {
        UUID usuarioId = usuarioId(contexto);
        registrarUso(contexto, "menuDelDia");
        LocalDate dia = fecha != null ? fecha : hoy();
        List<Plato> platos = menuServicio.comidasDelDia(usuarioId, dia).stream()
                .map(HerramientasAsistente::plato)
                .toList();
        Nutrientes total = platos.stream().map(Plato::nutrientes).filter(Objects::nonNull)
                .reduce(Nutrientes.CERO, Nutrientes::sumar).redondear();
        return new MenuDia(dia, platos, total);
    }

    @Tool(name = "tendencias", description = "Patrones detectados en los últimos 28 días de datos del usuario (por "
            + "ejemplo, proteína baja varios días o pocos hidratos los días de entreno), con sus cifras ya calculadas. "
            + "Úsala para preguntas sobre cómo lo está haciendo en general o qué debería mejorar.")
    public Tendencias tendencias(ToolContext contexto) {
        UUID usuarioId = usuarioId(contexto);
        registrarUso(contexto, "tendencias");
        List<InsightResponse> vigentes = insightServicio.vigentes(usuarioId);
        return new Tendencias(vigentes.stream()
                .map(insight -> new Tendencia(insight.tipo().name(), insight.severidad().name(), insight.titulo(),
                        insight.texto(), insight.periodoDesde(), insight.periodoHasta()))
                .toList());
    }

    // ---------------------------------------------------------------------------------------------
    // Contexto
    // ---------------------------------------------------------------------------------------------

    /**
     * El usuario del contexto de la herramienta (un UUID o su texto).
     *
     * @throws IllegalStateException si el contexto no lo trae (nunca se toma de los parámetros del modelo)
     */
    static UUID usuarioId(ToolContext contexto) {
        Object valor = contexto == null || contexto.getContext() == null ? null
                : contexto.getContext().get(CLAVE_USUARIO);
        if (valor instanceof UUID uuid) {
            return uuid;
        }
        if (valor instanceof String texto) {
            return UUID.fromString(texto);
        }
        throw new IllegalStateException("La herramienta se ha llamado sin el usuario en el contexto");
    }

    /** Añade la herramienta a la lista del contexto, si la hay y se puede modificar. */
    @SuppressWarnings("unchecked")
    static void registrarUso(ToolContext contexto, String herramienta) {
        if (contexto != null && contexto.getContext() != null
                && contexto.getContext().get(CLAVE_HERRAMIENTAS) instanceof List<?> lista) {
            try {
                ((List<Object>) lista).add(herramienta);
            } catch (UnsupportedOperationException inmutable) {
                // Una lista que no se puede modificar no impide responder
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** Ambas fechas obligatorias, hasta ≥ desde y como mucho {@code maximo} días contando los dos extremos. */
    private static void validarRango(LocalDate desde, LocalDate hasta, int maximo) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Indica las dos fechas, desde y hasta, en formato AAAA-MM-DD");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha hasta no puede ser anterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > maximo) {
            throw new IllegalArgumentException("El rango no puede superar los " + maximo + " días");
        }
    }

    private static Comida comida(RegistroComidaResponse comida) {
        return new Comida(comida.tipoComida().name(), comida.hora(), comida.totales().redondear(),
                comida.alimentos().stream()
                        .map(alimento -> alimento.nombre() + " ("
                                + alimento.gramos().setScale(0, RoundingMode.HALF_UP).toPlainString() + " g)")
                        .toList());
    }

    private static Plato plato(ComidaPendienteResponse comida) {
        return new Plato(comida.tipoComida().name(), comida.receta() == null ? null : comida.receta().nombre(),
                comida.raciones(), comida.nutrientes(), false);
    }

    private static Plato plato(ComidaPlanificadaResponse comida) {
        return new Plato(comida.tipoComida().name(), comida.receta() == null ? null : comida.receta().nombre(),
                comida.raciones(), comida.nutrientes(), comida.consumida());
    }
}
