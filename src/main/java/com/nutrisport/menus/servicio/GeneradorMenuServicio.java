package com.nutrisport.menus.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.UsoIaServicio;
import com.nutrisport.menus.dto.GenerarMenuRequest;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.menus.recomendacion.ContextoGeneracion;
import com.nutrisport.menus.recomendacion.MotorRecomendacion;
import com.nutrisport.menus.recomendacion.SeleccionFranja;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.PreferenciaAlimentaria;
import com.nutrisport.perfil.entidad.TipoPreferencia;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.repositorio.PreferenciaAlimentariaRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaFavorita;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaFavoritaRepositorio;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generación de menús diarios y semanales con el motor de reglas (§4.5 y §7.5, Ampliación):
 * <ol>
 * <li>Perfil (sin perfil → 422 PERFIL_REQUERIDO), restricciones estrictas, favoritos y recetas candidatas.</li>
 * <li>Para cada día (1, o 7 consecutivos: de lunes a domingo si empieza en lunes), el objetivo de
 * {@link ObjetivoDiarioServicio} (con los entrenamientos planificados) y el motor. Una receta usada en los 2 días
 * anteriores del menú puntúa peor.</li>
 * <li>Si se pide, una explicación breve de la IA con solo datos ya calculados; si la IA falla, una de plantilla.
 * Los avisos del motor se añaden a la explicación.</li>
 * <li>Se guarda el menú ACTIVO con generadoAutomaticamente = true.</li>
 * </ol>
 * La llamada a la IA va fuera de las transacciones: primero se calcula todo (y se guardan los objetivos), después
 * se pide la explicación y al final se guarda el menú.
 * <p>
 * Favoritas: las recetas marcadas como favoritas y las que llevan un alimento o son de una categoría con
 * preferencia FAVORITO.
 */
@Service
public class GeneradorMenuServicio {

    static final String PLANTILLA_EXPLICACION = "explicacion-menu";
    static final int DIAS_SEMANA = 7;
    /** Días anteriores en los que una receta ya usada penaliza (+25). */
    static final int DIAS_VARIEDAD = 2;
    private static final int LONGITUD_MAXIMA_EXPLICACION = 2000;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Logger LOG = LoggerFactory.getLogger(GeneradorMenuServicio.class);

    /** Comidas de un día ya elegidas por el motor (por id: se guardan en otra transacción). */
    record DiaGenerado(LocalDate fecha, List<Comida> comidas, List<String> avisos) {
    }

    record Comida(TipoComida tipoComida, UUID recetaId, BigDecimal raciones) {
    }

    /** Lo que el motor ha generado y los datos (ya calculados) para la explicación. */
    record Plan(List<DiaGenerado> dias, Map<String, Object> datosExplicacion) {
    }

    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final RestriccionesServicio restriccionesServicio;
    private final PreferenciaAlimentariaRepositorio preferenciaRepositorio;
    private final RecetaFavoritaRepositorio favoritaRepositorio;
    private final RecetaRepositorio recetaRepositorio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final MenuServicio menuServicio;
    private final MenuSemanalRepositorio menuRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final GeneracionTextoPuerto generacionTexto;
    private final UsoIaServicio usoIaServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;
    private final TransactionTemplate escritura;
    private final MotorRecomendacion motor = new MotorRecomendacion();

    public GeneradorMenuServicio(PerfilDeportivoRepositorio perfilRepositorio,
            RestriccionesServicio restriccionesServicio, PreferenciaAlimentariaRepositorio preferenciaRepositorio,
            RecetaFavoritaRepositorio favoritaRepositorio, RecetaRepositorio recetaRepositorio,
            ObjetivoDiarioServicio objetivoDiarioServicio, MenuServicio menuServicio,
            MenuSemanalRepositorio menuRepositorio, UsuarioRepositorio usuarioRepositorio,
            GeneracionTextoPuerto generacionTexto, UsoIaServicio usoIaServicio, SeguridadUtils seguridadUtils,
            Clock reloj, PlatformTransactionManager transacciones) {
        this.perfilRepositorio = perfilRepositorio;
        this.restriccionesServicio = restriccionesServicio;
        this.preferenciaRepositorio = preferenciaRepositorio;
        this.favoritaRepositorio = favoritaRepositorio;
        this.recetaRepositorio = recetaRepositorio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.menuServicio = menuServicio;
        this.menuRepositorio = menuRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.generacionTexto = generacionTexto;
        this.usoIaServicio = usoIaServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
    }

    /**
     * @throws PeticionInvalidaException 400 si fechaInicio es anterior a hoy
     * @throws ReglaNegocioException     422 PERFIL_REQUERIDO sin perfil; 422 SIN_RECETAS_COMPATIBLES si ninguna
     *                                   receta pasa el filtro duro
     */
    public MenuResponse generar(GenerarMenuRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        if (peticion.fechaInicio().isBefore(LocalDate.now(reloj))) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos",
                    List.of(new ErrorCampo("fechaInicio", "La fecha de inicio no puede ser anterior a hoy")));
        }
        int numeroDias = peticion.tipo() == TipoMenu.SEMANAL ? DIAS_SEMANA : 1;
        LocalDate fin = peticion.fechaInicio().plusDays(numeroDias - 1L);

        Plan plan = escritura.execute(estado -> planificar(usuarioId, peticion, fin));

        String explicacion = Boolean.TRUE.equals(peticion.conExplicacion())
                ? explicar(usuarioId, plan.datosExplicacion())
                : null;
        String conAvisos = conAvisos(explicacion, plan.dias());

        return escritura.execute(estado -> guardar(usuarioId, peticion, fin, plan, conAvisos));
    }

    // ---------------------------------------------------------------------------------------------
    // 1 y 2: datos del usuario y motor
    // ---------------------------------------------------------------------------------------------

    private Plan planificar(UUID usuarioId, GenerarMenuRequest peticion, LocalDate fin) {
        PerfilDeportivo perfil = perfilRequerido(usuarioId);
        List<Receta> candidatas = recetaRepositorio.candidatasParaMenu(usuarioId);
        ContextoGeneracion contexto = contexto(usuarioId, perfil, candidatas);
        RestriccionesUsuario restricciones = contexto.restricciones();
        exigirCandidatas(contexto, candidatas);

        Set<LocalDate> diasEntreno = menuServicio.diasDeEntreno(usuarioId, peticion.fechaInicio(), fin);
        List<DiaGenerado> dias = new ArrayList<>();
        List<Set<UUID>> usadasPorDia = new ArrayList<>();
        BigDecimal kcalObjetivo = BigDecimal.ZERO;
        BigDecimal kcalPlanificado = BigDecimal.ZERO;
        int diasConAviso = 0;
        for (LocalDate fecha = peticion.fechaInicio(); !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
            ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.obtener(usuarioId, fecha);
            Set<UUID> recientes = new HashSet<>();
            usadasPorDia.subList(Math.max(0, usadasPorDia.size() - DIAS_VARIEDAD), usadasPorDia.size())
                    .forEach(recientes::addAll);

            MotorRecomendacion.ResultadoDia resultado = motor.generarDia(contexto, objetivo.total(),
                    diasEntreno.contains(fecha), candidatas, recientes, List.of());

            List<Comida> comidas = resultado.selecciones().stream()
                    .map(s -> new Comida(s.tipoComida(), s.receta().getId(), s.raciones()))
                    .toList();
            usadasPorDia.add(resultado.selecciones().stream().map(SeleccionFranja::receta).map(Receta::getId)
                    .collect(Collectors.toSet()));
            dias.add(new DiaGenerado(fecha, comidas, resultado.avisos()));
            kcalObjetivo = kcalObjetivo.add(objetivo.total().kcal());
            kcalPlanificado = kcalPlanificado.add(resultado.total().kcal());
            if (!resultado.avisos().isEmpty()) {
                diasConAviso++;
            }
        }
        return new Plan(dias, datosExplicacion(peticion.tipo(), dias.size(), kcalObjetivo, kcalPlanificado,
                diasEntreno, restricciones, diasConAviso));
    }

    /** Perfil con objetivo calculado; si no, 422 PERFIL_REQUERIDO. */
    PerfilDeportivo perfilRequerido(UUID usuarioId) {
        return perfilRepositorio.findByUsuarioId(usuarioId)
                .filter(encontrado -> encontrado.getObjetivoKcal() != null)
                .orElseThrow(() -> new ReglaNegocioException("PERFIL_REQUERIDO",
                        "Completa tu perfil deportivo para generar un menú"));
    }

    /**
     * Datos del usuario para el motor: restricciones estrictas, favoritas y, si hay perfil, su tiempo máximo de cocina
     * y sus comidas al día (sin perfil: sin límite de tiempo y 3 comidas).
     */
    ContextoGeneracion contexto(UUID usuarioId, PerfilDeportivo perfil, List<Receta> candidatas) {
        Integer tiempoMax = perfil == null || perfil.getTiempoMaxCocinaMin() == null ? null
                : perfil.getTiempoMaxCocinaMin().intValue();
        int comidasPorDia = perfil == null || perfil.getComidasPorDia() == null ? 3 : perfil.getComidasPorDia();
        return new ContextoGeneracion(restriccionesServicio.estrictasDe(usuarioId), favoritas(usuarioId, candidatas),
                tiempoMax, comidasPorDia);
    }

    /** 422 SIN_RECETAS_COMPATIBLES si ninguna receta pasa el filtro duro en ninguna franja. */
    static void exigirCandidatas(ContextoGeneracion contexto, List<Receta> candidatas) {
        if (!MotorRecomendacion.hayCandidatas(contexto, candidatas)) {
            throw new ReglaNegocioException("SIN_RECETAS_COMPATIBLES",
                    "No hay recetas publicadas compatibles con tus restricciones para generar un menú");
        }
    }

    /** Recetas favoritas, o con un alimento o de una categoría (de receta o del alimento) con preferencia FAVORITO. */
    private Set<UUID> favoritas(UUID usuarioId, List<Receta> candidatas) {
        Set<UUID> favoritas = favoritaRepositorio.findByIdUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
                .map(RecetaFavorita::getReceta)
                .map(Receta::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<UUID> alimentos = new HashSet<>();
        Set<UUID> categorias = new HashSet<>();
        for (PreferenciaAlimentaria preferencia : preferenciaRepositorio.findByUsuarioId(usuarioId)) {
            if (preferencia.getTipo() == TipoPreferencia.FAVORITO) {
                if (preferencia.getAlimentoId() != null) {
                    alimentos.add(preferencia.getAlimentoId());
                }
                if (preferencia.getCategoriaId() != null) {
                    categorias.add(preferencia.getCategoriaId());
                }
            }
        }
        if (alimentos.isEmpty() && categorias.isEmpty()) {
            return favoritas;
        }
        for (Receta receta : candidatas) {
            boolean deCategoria = receta.getCategoria() != null && categorias.contains(receta.getCategoria().getId());
            boolean conAlimento = receta.getIngredientes().stream()
                    .map(RecetaIngrediente::getAlimento)
                    .filter(alimento -> alimento != null)
                    .anyMatch(alimento -> alimentos.contains(alimento.getId())
                            || (alimento.getCategoria() != null && categorias.contains(alimento.getCategoria().getId())));
            if (deCategoria || conAlimento) {
                favoritas.add(receta.getId());
            }
        }
        return favoritas;
    }

    // ---------------------------------------------------------------------------------------------
    // 3: explicación
    // ---------------------------------------------------------------------------------------------

    /** Solo datos ya calculados: la IA no recibe recetas ni cifras que tenga que calcular. */
    private static Map<String, Object> datosExplicacion(TipoMenu tipo, int dias, BigDecimal kcalObjetivo,
            BigDecimal kcalPlanificado, Set<LocalDate> diasEntreno, RestriccionesUsuario restricciones,
            int diasConAviso) {
        BigDecimal numero = BigDecimal.valueOf(dias);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", tipo == TipoMenu.SEMANAL ? "semanal" : "diario");
        datos.put("dias", dias);
        datos.put("objetivoMedioKcal", kcalObjetivo.divide(numero, 0, RoundingMode.HALF_UP).toPlainString());
        datos.put("mediaKcal", kcalPlanificado.divide(numero, 0, RoundingMode.HALF_UP).toPlainString());
        datos.put("ajustePorcentaje", kcalObjetivo.signum() == 0 ? "—"
                : kcalPlanificado.multiply(CIEN).divide(kcalObjetivo, 0, RoundingMode.HALF_UP).toPlainString());
        datos.put("diasEntreno", diasEntreno.isEmpty() ? "ninguno"
                : diasEntreno.size() + " (" + diasEntreno.stream().sorted()
                        .map(fecha -> fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ES))
                        .collect(Collectors.joining(", ")) + ")");
        datos.put("restricciones", describir(restricciones));
        datos.put("diasConAviso", diasConAviso);
        return datos;
    }

    /** Explicación de la IA; si falla o no responde nada, la de plantilla. Se registra el uso. */
    private String explicar(UUID usuarioId, Map<String, Object> datos) {
        long inicio = System.nanoTime();
        try {
            ResultadoIa<String> resultado = generacionTexto.generar(PLANTILLA_EXPLICACION, datos);
            usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, resultado, milisegundosDesde(inicio), true);
            String texto = resultado.contenido() == null ? "" : resultado.contenido().strip();
            if (!texto.isEmpty()) {
                return texto.length() <= LONGITUD_MAXIMA_EXPLICACION ? texto
                        : texto.substring(0, LONGITUD_MAXIMA_EXPLICACION);
            }
        } catch (RuntimeException e) {
            LOG.warn("No se ha podido generar la explicación del menú con IA; se usa la de plantilla: {}",
                    e.toString());
            try {
                usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, null, milisegundosDesde(inicio), false);
            } catch (RuntimeException registro) {
                LOG.warn("No se ha podido registrar el uso fallido de la IA: {}", registro.toString());
            }
        }
        return explicacionDePlantilla(datos);
    }

    /** Explicación sin IA con los mismos datos. */
    static String explicacionDePlantilla(Map<String, Object> datos) {
        StringBuilder texto = new StringBuilder();
        texto.append("Menú ").append(datos.get("tipo")).append(" de ").append(datos.get("dias"))
                .append(Integer.valueOf(1).equals(datos.get("dias")) ? " día" : " días")
                .append(" ajustado a un objetivo medio de ").append(datos.get("objetivoMedioKcal"))
                .append(" kcal al día: la energía planificada media es de ").append(datos.get("mediaKcal"))
                .append(" kcal (").append(datos.get("ajustePorcentaje")).append(" % del objetivo). ");
        texto.append("ninguno".equals(datos.get("diasEntreno"))
                ? "No hay entrenamientos planificados en estas fechas. "
                : "Días de entreno: " + datos.get("diasEntreno") + ", con su gasto sumado al objetivo. ");
        texto.append("ninguna".equals(datos.get("restricciones"))
                ? "No tienes restricciones estrictas."
                : "Respeta tus restricciones: " + datos.get("restricciones") + ".");
        return texto.toString();
    }

    /** La explicación con los avisos del motor al final (o solo los avisos si no hay explicación). */
    private static String conAvisos(String explicacion, List<DiaGenerado> dias) {
        List<String> avisos = dias.stream()
                .flatMap(dia -> dia.avisos().stream().map(aviso -> "- " + dia.fecha().format(FECHA) + ": " + aviso))
                .toList();
        if (avisos.isEmpty()) {
            return explicacion;
        }
        String lista = "Avisos:\n" + String.join("\n", avisos);
        return explicacion == null ? lista : explicacion + "\n\n" + lista;
    }

    /** Restricciones en palabras ("alergia a gluten, dieta vegana…") o "ninguna". */
    static String describir(RestriccionesUsuario restricciones) {
        List<String> partes = new ArrayList<>();
        restricciones.alergenos().stream().sorted()
                .forEach(alergeno -> partes.add("alergia a " + alergeno.toLowerCase(Locale.ROOT).replace('_', ' ')));
        if (restricciones.intoleranciaLactosa()) {
            partes.add("sin lactosa");
        }
        restricciones.dietas().stream().sorted()
                .filter(dieta -> dieta != Dieta.HALAL && dieta != Dieta.KOSHER)
                .forEach(dieta -> partes.add("dieta " + dieta.name().toLowerCase(Locale.ROOT)));
        int rechazos = restricciones.alimentosRechazados().size() + restricciones.categoriasRechazadas().size();
        if (rechazos > 0) {
            partes.add(rechazos + (rechazos == 1 ? " alimento o categoría rechazados" : " alimentos o categorías rechazados"));
        }
        return partes.isEmpty() ? "ninguna" : String.join(", ", partes);
    }

    // ---------------------------------------------------------------------------------------------
    // 4: guardado
    // ---------------------------------------------------------------------------------------------

    private MenuResponse guardar(UUID usuarioId, GenerarMenuRequest peticion, LocalDate fin, Plan plan,
            String explicacion) {
        MenuSemanal menu = new MenuSemanal();
        menu.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        menu.setNombre(peticion.nombre() != null ? peticion.nombre().strip() : nombrePorDefecto(peticion));
        menu.setTipo(peticion.tipo());
        menu.setFechaInicio(peticion.fechaInicio());
        menu.setFechaFin(fin);
        menu.setEstado(EstadoMenu.ACTIVO);
        menu.setGeneradoAutomaticamente(true);
        menu.setExplicacion(explicacion);
        for (DiaGenerado dia : plan.dias()) {
            for (Comida comida : dia.comidas()) {
                menu.anadirComida(new ComidaPlanificada(dia.fecha(), comida.tipoComida(),
                        recetaRepositorio.getReferenceById(comida.recetaId()), comida.raciones()));
            }
        }
        return menuServicio.respuesta(menuRepositorio.saveAndFlush(menu), usuarioId);
    }

    private static String nombrePorDefecto(GenerarMenuRequest peticion) {
        return (peticion.tipo() == TipoMenu.SEMANAL ? "Menú semanal del " : "Menú del ")
                + peticion.fechaInicio().format(FECHA);
    }

    private static long milisegundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
