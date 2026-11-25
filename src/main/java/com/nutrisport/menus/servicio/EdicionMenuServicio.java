package com.nutrisport.menus.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comidas.servicio.ComidaServicio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.menus.dto.ActualizarComidaPlanificadaRequest;
import com.nutrisport.menus.dto.AlternativaResponse;
import com.nutrisport.menus.dto.AnadirComidaRequest;
import com.nutrisport.menus.dto.DuplicarMenuRequest;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.recomendacion.ContextoGeneracion;
import com.nutrisport.menus.recomendacion.MotorRecomendacion;
import com.nutrisport.menus.recomendacion.PuntuadorRecetas;
import com.nutrisport.menus.recomendacion.RepartoFranjas;
import com.nutrisport.menus.recomendacion.SeleccionFranja;
import com.nutrisport.menus.repositorio.ComidaPlanificadaRepositorio;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import com.nutrisport.recetas.servicio.RecetaServicio;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edición de los menús del usuario autenticado (§3.D y §7.5, Ampliación): alternativas equivalentes para una comida,
 * cambios manuales (receta, raciones, día, franja y bloqueo), añadir y quitar comidas, regenerar lo no bloqueado,
 * reutilizar un menú en otras fechas y registrar una comida planificada como consumida. Los menús de otro usuario
 * responden 404.
 * <p>
 * Una receta elegida a mano (al añadir o cambiar una comida) debe ser visible para el usuario, estar publicada o ser
 * propia, tener cálculo COMPLETO o APROXIMADO (422 NUTRICION_INCOMPLETA) y cumplir sus restricciones estrictas
 * ({@link MotorRecomendacion#cumpleRestricciones}; 422 RECETA_INCOMPATIBLE). No se le exige la franja ni el tiempo de
 * cocina: eso solo lo aplica el motor al proponer.
 */
@Service
@Transactional
public class EdicionMenuServicio {

    static final int NUMERO_ALTERNATIVAS = 3;
    /** Energía de una alternativa respecto a la comida original. */
    static final BigDecimal TOLERANCIA_ALTERNATIVA_PCT = new BigDecimal("15");
    static final int MAXIMO_DISPONIBLES = 50;
    /** Peso de una franja que no está en el reparto del perfil (pre-entreno, otro...) al repartir el hueco del día. */
    static final BigDecimal PORCENTAJE_FUERA_DE_REPARTO = new BigDecimal("10");
    static final int LONGITUD_MAXIMA_NOMBRE = 100;

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    /** Orden estable para los empates: nombre y después id (como el motor). */
    private static final Comparator<Receta> ORDEN_ESTABLE = Comparator
            .comparing((Receta receta) -> receta.getNombre() == null ? "" : receta.getNombre())
            .thenComparing(receta -> receta.getId().toString());

    /** Una receta con las raciones que mejor ajustan, lo que aporta y su puntuación (menor es mejor). */
    private record Propuesta(Receta receta, BigDecimal raciones, Nutrientes aporte, double puntuacion) {
    }

    private final MenuSemanalRepositorio menuRepositorio;
    private final ComidaPlanificadaRepositorio comidaRepositorio;
    private final RecetaRepositorio recetaRepositorio;
    private final RegistroComidaRepositorio registroRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final MenuServicio menuServicio;
    private final GeneradorMenuServicio generadorMenuServicio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final RestriccionesServicio restriccionesServicio;
    private final RecetaServicio recetaServicio;
    private final ComidaServicio comidaServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;
    private final MotorRecomendacion motor = new MotorRecomendacion();

    public EdicionMenuServicio(MenuSemanalRepositorio menuRepositorio, ComidaPlanificadaRepositorio comidaRepositorio,
            RecetaRepositorio recetaRepositorio, RegistroComidaRepositorio registroRepositorio,
            PerfilDeportivoRepositorio perfilRepositorio, UsuarioRepositorio usuarioRepositorio,
            MenuServicio menuServicio, GeneradorMenuServicio generadorMenuServicio,
            ObjetivoDiarioServicio objetivoDiarioServicio, RestriccionesServicio restriccionesServicio,
            RecetaServicio recetaServicio, ComidaServicio comidaServicio,
            SeguridadUtils seguridadUtils, Clock reloj) {
        this.menuRepositorio = menuRepositorio;
        this.comidaRepositorio = comidaRepositorio;
        this.recetaRepositorio = recetaRepositorio;
        this.registroRepositorio = registroRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.menuServicio = menuServicio;
        this.generadorMenuServicio = generadorMenuServicio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.restriccionesServicio = restriccionesServicio;
        this.recetaServicio = recetaServicio;
        this.comidaServicio = comidaServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------------------
    // Alternativas y recetas disponibles
    // ---------------------------------------------------------------------------------------------

    /**
     * Hasta 3 recetas que pueden sustituir a la de la comida: pasan el filtro duro del motor para su franja
     * ({@link MotorRecomendacion#esCandidata}), no son la actual ni otra del mismo día y, con las raciones que mejor
     * ajustan a la comida original, su energía queda en ±15 % de la de esta. Ordenadas con {@link PuntuadorRecetas}
     * respecto a los nutrientes de la comida original (favoritas, usadas en los 2 días anteriores del menú, día de
     * entreno). Vacía si la receta actual no tiene cálculo nutricional.
     */
    @Transactional(readOnly = true)
    public List<AlternativaResponse> alternativas(UUID menuId, UUID comidaId) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuServicio.propio(menuId, usuarioId);
        ComidaPlanificada comida = comidaDe(menu, comidaId);
        Nutrientes original = MenuServicio.nutrientes(comida);
        if (original == null || original.kcal().signum() <= 0) {
            return List.of();
        }
        LocalDate fecha = comida.getFecha();
        List<Receta> candidatas = recetaRepositorio.candidatasParaMenu(usuarioId);
        ContextoGeneracion contexto = generadorMenuServicio.contexto(usuarioId,
                perfilRepositorio.findByUsuarioId(usuarioId).orElse(null), candidatas);
        Set<UUID> delMismoDia = recetasEntre(menu, fecha, fecha);
        delMismoDia.add(comida.getReceta().getId());
        Set<UUID> recientes = recetasEntre(menu, fecha.minusDays(GeneradorMenuServicio.DIAS_VARIEDAD),
                fecha.minusDays(1));
        boolean diaEntreno = menuServicio.diasDeEntreno(usuarioId, fecha, fecha).contains(fecha);

        List<Propuesta> elegidas = candidatas.stream()
                .filter(receta -> !delMismoDia.contains(receta.getId()))
                .filter(receta -> MotorRecomendacion.esCandidata(receta, contexto, comida.getTipoComida()))
                .sorted(ORDEN_ESTABLE)
                .map(receta -> {
                    BigDecimal raciones = PuntuadorRecetas.mejoresRaciones(receta, original);
                    return new Propuesta(receta, raciones, aporte(receta, raciones),
                            PuntuadorRecetas.puntuar(receta, raciones, original, contexto, recientes, diaEntreno,
                                    comida.getTipoComida()));
                })
                .filter(propuesta -> dentro(propuesta.aporte().kcal(), original.kcal(), TOLERANCIA_ALTERNATIVA_PCT))
                .sorted(Comparator.comparingDouble(Propuesta::puntuacion))
                .limit(NUMERO_ALTERNATIVAS)
                .toList();
        return respuestas(usuarioId, elegidas, original);
    }

    /**
     * Recetas que se pueden añadir a ese día y franja: publicadas (públicas o propias), con cálculo COMPLETO o
     * APROXIMADO, aptas para la franja y que cumplen las restricciones estrictas; con {@code q}, solo las que lo
     * contienen en el nombre (sin distinguir mayúsculas ni tildes). Como mucho 50.
     * <p>
     * Con perfil se ordenan por su ajuste ({@link PuntuadorRecetas#ajuste}) a la parte del hueco del día que corresponde
     * a la franja, con las raciones que mejor encajan: hueco = objetivo del día − lo registrado − lo planificado aún no
     * consumido en menús ACTIVO (nunca negativo), repartido entre la franja y las del reparto del perfil que aún no
     * tienen nada ese día según sus porcentajes. Sin perfil, por nombre y con 1 ración.
     */
    public List<AlternativaResponse> recetasDisponibles(LocalDate fecha, TipoComida tipoComida, String q) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        PerfilDeportivo perfil = perfilRepositorio.findByUsuarioId(usuarioId)
                .filter(encontrado -> encontrado.getObjetivoKcal() != null)
                .orElse(null);
        List<Receta> candidatas = recetaRepositorio.candidatasParaMenu(usuarioId);
        ContextoGeneracion contexto = generadorMenuServicio.contexto(usuarioId, perfil, candidatas);
        String texto = q == null || q.isBlank() ? null : TextoNormalizado.normalizar(q);
        Nutrientes objetivoFranja = perfil == null ? null : objetivoDeFranja(usuarioId, contexto, fecha, tipoComida);

        List<Propuesta> propuestas = candidatas.stream()
                .filter(receta -> receta.getFranjasAptas().contains(tipoComida.name()))
                .filter(receta -> conNutrientes(receta))
                .filter(receta -> MotorRecomendacion.cumpleRestricciones(receta, contexto.restricciones()))
                .filter(receta -> texto == null || (receta.getNombreNormalizado() != null
                        && receta.getNombreNormalizado().contains(texto)))
                .sorted(ORDEN_ESTABLE)
                .map(receta -> {
                    if (objetivoFranja == null) {
                        return new Propuesta(receta, BigDecimal.ONE, aporte(receta, BigDecimal.ONE), 0);
                    }
                    BigDecimal raciones = PuntuadorRecetas.mejoresRaciones(receta, objetivoFranja);
                    Nutrientes aporte = aporte(receta, raciones);
                    return new Propuesta(receta, raciones, aporte, PuntuadorRecetas.ajuste(aporte, objetivoFranja));
                })
                .sorted(Comparator.comparingDouble(Propuesta::puntuacion))
                .limit(MAXIMO_DISPONIBLES)
                .toList();
        return respuestas(usuarioId, propuestas, objetivoFranja);
    }

    // ---------------------------------------------------------------------------------------------
    // Cambios de comidas
    // ---------------------------------------------------------------------------------------------

    /**
     * Cambia la receta, las raciones, el día, la franja o el bloqueo de una comida; los campos nulos no cambian.
     *
     * @throws ConflictoException           409 COMIDA_CONSUMIDA si ya está consumida
     * @throws PeticionInvalidaException    400 si la fecha queda fuera del menú
     * @throws RecursoNoEncontradoException 404 si el menú, la comida o la receta no existen
     * @throws ReglaNegocioException        422 RECETA_NO_PUBLICADA, NUTRICION_INCOMPLETA o RECETA_INCOMPATIBLE
     */
    public MenuResponse actualizarComida(UUID menuId, UUID comidaId, ActualizarComidaPlanificadaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuServicio.propio(menuId, usuarioId);
        ComidaPlanificada comida = comidaDe(menu, comidaId);
        if (comida.isConsumida()) {
            throw new ConflictoException("COMIDA_CONSUMIDA", "La comida ya está consumida y no se puede cambiar");
        }
        if (peticion.fecha() != null) {
            exigirDentro(menu, peticion.fecha());
        }
        Receta receta = peticion.recetaId() == null || peticion.recetaId().equals(comida.getReceta().getId())
                ? comida.getReceta()
                : recetaUtilizable(peticion.recetaId(), usuarioId);

        comida.setReceta(receta);
        if (peticion.fecha() != null) {
            comida.setFecha(peticion.fecha());
        }
        if (peticion.raciones() != null) {
            comida.setRaciones(peticion.raciones());
        }
        if (peticion.tipoComida() != null) {
            comida.setTipoComida(peticion.tipoComida());
        }
        if (peticion.bloqueada() != null) {
            comida.setBloqueada(peticion.bloqueada());
        }
        return menuServicio.respuesta(menuRepositorio.saveAndFlush(menu), usuarioId);
    }

    /**
     * Añade una receta al menú en ese día y franja.
     *
     * @throws PeticionInvalidaException    400 si la fecha está fuera del menú
     * @throws RecursoNoEncontradoException 404 si el menú o la receta no existen
     * @throws ReglaNegocioException        422 RECETA_NO_PUBLICADA, NUTRICION_INCOMPLETA o RECETA_INCOMPATIBLE
     */
    public MenuResponse anadirComida(UUID menuId, AnadirComidaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuServicio.propio(menuId, usuarioId);
        exigirDentro(menu, peticion.fecha());
        Receta receta = recetaUtilizable(peticion.recetaId(), usuarioId);
        menu.anadirComida(new ComidaPlanificada(peticion.fecha(), peticion.tipoComida(), receta, peticion.raciones()));
        return menuServicio.respuesta(menuRepositorio.saveAndFlush(menu), usuarioId);
    }

    /** Quita una comida del menú; si ya estaba registrada, la comida registrada se conserva. */
    public void quitarComida(UUID menuId, UUID comidaId) {
        MenuSemanal menu = menuServicio.propio(menuId, seguridadUtils.usuarioActualId());
        menu.quitarComida(comidaDe(menu, comidaId));
    }

    // ---------------------------------------------------------------------------------------------
    // Regenerar y duplicar
    // ---------------------------------------------------------------------------------------------

    /**
     * Vuelve a generar con el motor, día a día, las comidas no bloqueadas y no consumidas. Las bloqueadas y las
     * consumidas no cambian: ocupan su franja y su energía se descuenta del objetivo del día. Como al generar, una
     * receta usada en los 2 días anteriores del menú puntúa peor.
     *
     * @throws ReglaNegocioException 422 PERFIL_REQUERIDO sin perfil; 422 SIN_RECETAS_COMPATIBLES si ninguna receta
     *                               pasa el filtro duro
     */
    public MenuResponse regenerar(UUID menuId) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuServicio.propio(menuId, usuarioId);
        PerfilDeportivo perfil = generadorMenuServicio.perfilRequerido(usuarioId);
        List<Receta> candidatas = recetaRepositorio.candidatasParaMenu(usuarioId);
        ContextoGeneracion contexto = generadorMenuServicio.contexto(usuarioId, perfil, candidatas);
        GeneradorMenuServicio.exigirCandidatas(contexto, candidatas);
        Set<LocalDate> diasEntreno = menuServicio.diasDeEntreno(usuarioId, menu.getFechaInicio(), menu.getFechaFin());
        Map<LocalDate, List<ComidaPlanificada>> porDia = menu.getComidas().stream()
                .collect(Collectors.groupingBy(ComidaPlanificada::getFecha));

        List<Set<UUID>> usadasPorDia = new ArrayList<>();
        for (LocalDate fecha = menu.getFechaInicio(); !fecha.isAfter(menu.getFechaFin()); fecha = fecha.plusDays(1)) {
            List<ComidaPlanificada> delDia = porDia.getOrDefault(fecha, List.of());
            List<SeleccionFranja> fijas = new ArrayList<>();
            for (ComidaPlanificada comida : delDia) {
                if (comida.isBloqueada() || comida.isConsumida()) {
                    fijas.add(new SeleccionFranja(comida.getTipoComida(), comida.getReceta(), comida.getRaciones(),
                            true));
                } else {
                    menu.quitarComida(comida);
                }
            }
            Set<UUID> recientes = new HashSet<>();
            usadasPorDia.subList(Math.max(0, usadasPorDia.size() - GeneradorMenuServicio.DIAS_VARIEDAD),
                    usadasPorDia.size()).forEach(recientes::addAll);
            ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.obtener(usuarioId, fecha);

            MotorRecomendacion.ResultadoDia resultado = motor.generarDia(contexto, objetivo.total(),
                    diasEntreno.contains(fecha), candidatas, recientes, fijas);

            for (SeleccionFranja seleccion : resultado.selecciones()) {
                if (!seleccion.bloqueada()) {
                    menu.anadirComida(new ComidaPlanificada(fecha, seleccion.tipoComida(), seleccion.receta(),
                            seleccion.raciones()));
                }
            }
            usadasPorDia.add(resultado.selecciones().stream().map(s -> s.receta().getId()).collect(Collectors.toSet()));
        }
        return menuServicio.respuesta(menuRepositorio.saveAndFlush(menu), usuarioId);
    }

    /**
     * Copia el menú empezando en {@code fechaInicio}: las comidas se desplazan los mismos días y conservan su receta,
     * raciones, franja y bloqueo, pero no su consumo. La copia queda ACTIVA, no favorita y sin explicación (la del
     * original habla de otras fechas).
     *
     * @throws PeticionInvalidaException 400 si fechaInicio es anterior a hoy
     */
    public MenuResponse duplicar(UUID menuId, DuplicarMenuRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        if (peticion.fechaInicio().isBefore(LocalDate.now(reloj))) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS,
                    List.of(new ErrorCampo("fechaInicio", "La fecha de inicio no puede ser anterior a hoy")));
        }
        MenuSemanal original = menuServicio.propio(menuId, usuarioId);
        long desplazamiento = ChronoUnit.DAYS.between(original.getFechaInicio(), peticion.fechaInicio());

        MenuSemanal copia = new MenuSemanal();
        copia.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        copia.setNombre(peticion.nombre() != null ? peticion.nombre().strip() : nombreDeCopia(original.getNombre()));
        copia.setTipo(original.getTipo());
        copia.setFechaInicio(peticion.fechaInicio());
        copia.setFechaFin(original.getFechaFin().plusDays(desplazamiento));
        copia.setFavorito(false);
        copia.setEstado(EstadoMenu.ACTIVO);
        copia.setGeneradoAutomaticamente(original.isGeneradoAutomaticamente());
        for (ComidaPlanificada comida : original.getComidas()) {
            ComidaPlanificada nueva = new ComidaPlanificada(comida.getFecha().plusDays(desplazamiento),
                    comida.getTipoComida(), comida.getReceta(), comida.getRaciones());
            nueva.setBloqueada(comida.isBloqueada());
            copia.anadirComida(nueva);
        }
        return menuServicio.respuesta(menuRepositorio.saveAndFlush(copia), usuarioId);
    }

    // ---------------------------------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------------------------------

    /**
     * Registra la comida planificada como comida consumida (origen PLAN) con los ingredientes de la receta escalados a
     * sus raciones ({@link ComidaServicio#registrarPlanificada}) y la marca como consumida con ese registro.
     *
     * @param hora opcional
     * @throws ConflictoException        409 COMIDA_CONSUMIDA si ya estaba consumida
     * @throws PeticionInvalidaException 400 si la comida es de pasado mañana o después
     * @throws ReglaNegocioException     422 NUTRICION_INCOMPLETA si la receta no tiene ingredientes que registrar
     */
    public RegistroComidaResponse registrar(UUID menuId, UUID comidaId, LocalTime hora) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuServicio.propio(menuId, usuarioId);
        ComidaPlanificada comida = comidaDe(menu, comidaId);
        if (comida.isConsumida()) {
            throw new ConflictoException("COMIDA_CONSUMIDA", "La comida ya está registrada como consumida");
        }
        RegistroComidaResponse registro = comidaServicio.registrarPlanificada(comida.getReceta(),
                comida.getRaciones(), comida.getFecha(), comida.getTipoComida(), hora);
        RegistroComida registrada = registroRepositorio.getReferenceById(registro.id());
        comida.setRegistroComida(registrada);
        return registro;
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private static ComidaPlanificada comidaDe(MenuSemanal menu, UUID comidaId) {
        return menu.getComidas().stream()
                .filter(comida -> comida.getId().equals(comidaId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("La comida no existe"));
    }

    private static void exigirDentro(MenuSemanal menu, LocalDate fecha) {
        if (!menu.contiene(fecha)) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo("fecha",
                    "Debe estar dentro del menú, entre " + menu.getFechaInicio() + " y " + menu.getFechaFin())));
        }
    }

    /** Receta elegida a mano: visible, publicada o propia, con cálculo y compatible (ver la clase). */
    private Receta recetaUtilizable(UUID recetaId, UUID usuarioId) {
        Receta receta = recetaRepositorio.findWithIngredientesById(recetaId)
                .filter(encontrada -> encontrada.esVisiblePara(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe"));
        boolean propia = receta.getPropietario() != null && usuarioId.equals(receta.getPropietario().getId());
        if (!receta.isPublicada() && !propia) {
            throw new ReglaNegocioException("RECETA_NO_PUBLICADA", "La receta no está publicada");
        }
        if (receta.getEstadoNutricional() == EstadoNutricional.INCOMPLETO || !conNutrientes(receta)) {
            throw new ReglaNegocioException("NUTRICION_INCOMPLETA",
                    "La receta no tiene un cálculo nutricional completo y no se puede planificar");
        }
        if (!MotorRecomendacion.cumpleRestricciones(receta, restriccionesServicio.estrictasDe(usuarioId))) {
            throw new ReglaNegocioException("RECETA_INCOMPATIBLE",
                    "La receta no es compatible con tus restricciones alimentarias");
        }
        return receta;
    }

    private static boolean conNutrientes(Receta receta) {
        return receta.getKcalRacion() != null && receta.getProteinaRacion() != null
                && receta.getCarbohidratosRacion() != null && receta.getGrasaRacion() != null;
    }

    /** Ids de las recetas del menú entre esas fechas (ambas incluidas). */
    private static Set<UUID> recetasEntre(MenuSemanal menu, LocalDate desde, LocalDate hasta) {
        return menu.getComidas().stream()
                .filter(comida -> !comida.getFecha().isBefore(desde) && !comida.getFecha().isAfter(hasta))
                .map(comida -> comida.getReceta().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * La parte del hueco del día que corresponde a la franja: hueco × % de la franja / (% de la franja + % de las
     * franjas del reparto sin nada registrado ni planificado pendiente ese día).
     */
    private Nutrientes objetivoDeFranja(UUID usuarioId, ContextoGeneracion contexto, LocalDate fecha,
            TipoComida tipoComida) {
        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.obtener(usuarioId, fecha);
        List<RegistroComida> registradas = registroRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha);
        List<ComidaPlanificada> pendientes = comidaRepositorio.pendientesDelDia(usuarioId, fecha);

        Nutrientes ocupado = Nutrientes.CERO;
        Set<TipoComida> franjasOcupadas = new HashSet<>();
        for (RegistroComida registro : registradas) {
            ocupado = ocupado.sumar(new Nutrientes(registro.getTotalKcal(), registro.getTotalProteina(),
                    registro.getTotalCarbohidratos(), registro.getTotalGrasa()));
            franjasOcupadas.add(registro.getTipoComida());
        }
        for (ComidaPlanificada pendiente : pendientes) {
            Nutrientes nutrientes = MenuServicio.nutrientes(pendiente);
            if (nutrientes != null) {
                ocupado = ocupado.sumar(nutrientes);
            }
            franjasOcupadas.add(pendiente.getTipoComida());
        }
        Nutrientes hueco = restarSinNegativos(objetivo.total(), ocupado);

        LinkedHashMap<TipoComida, BigDecimal> reparto = RepartoFranjas.de(contexto.comidasPorDia());
        BigDecimal porcentajeFranja = reparto.getOrDefault(tipoComida, PORCENTAJE_FUERA_DE_REPARTO);
        BigDecimal porcentajeLibres = porcentajeFranja;
        for (Map.Entry<TipoComida, BigDecimal> franja : reparto.entrySet()) {
            if (franja.getKey() != tipoComida && !franjasOcupadas.contains(franja.getKey())) {
                porcentajeLibres = porcentajeLibres.add(franja.getValue());
            }
        }
        return hueco.escalar(porcentajeFranja.divide(porcentajeLibres, MathContext.DECIMAL64));
    }

    private List<AlternativaResponse> respuestas(UUID usuarioId, List<Propuesta> propuestas, Nutrientes referencia) {
        Map<UUID, RecetaResumenResponse> resumenes = recetaServicio.resumenes(usuarioId,
                propuestas.stream().map(Propuesta::receta).toList());
        return propuestas.stream()
                .map(propuesta -> new AlternativaResponse(resumenes.get(propuesta.receta().getId()),
                        propuesta.raciones(), propuesta.aporte(),
                        referencia == null ? null : diferenciaPct(propuesta.aporte().kcal(), referencia.kcal())))
                .toList();
    }

    private static Nutrientes aporte(Receta receta, BigDecimal raciones) {
        return PuntuadorRecetas.nutrientesPorRacion(receta).escalar(raciones).redondear();
    }

    private static boolean dentro(BigDecimal valor, BigDecimal referencia, BigDecimal toleranciaPct) {
        BigDecimal margen = referencia.multiply(toleranciaPct).divide(CIEN, MathContext.DECIMAL64);
        return valor.subtract(referencia).abs().compareTo(margen) <= 0;
    }

    /** (valor − referencia) / referencia × 100 con un decimal; nulo si la referencia no es positiva. */
    private static BigDecimal diferenciaPct(BigDecimal valor, BigDecimal referencia) {
        if (referencia.signum() <= 0) {
            return null;
        }
        return valor.subtract(referencia).multiply(CIEN).divide(referencia, 1, RoundingMode.HALF_UP);
    }

    private static Nutrientes restarSinNegativos(Nutrientes a, Nutrientes b) {
        return new Nutrientes(a.kcal().subtract(b.kcal()).max(BigDecimal.ZERO),
                a.proteina().subtract(b.proteina()).max(BigDecimal.ZERO),
                a.carbohidratos().subtract(b.carbohidratos()).max(BigDecimal.ZERO),
                a.grasa().subtract(b.grasa()).max(BigDecimal.ZERO));
    }

    private static String nombreDeCopia(String nombre) {
        String copia = "Copia de " + nombre;
        return copia.length() <= LONGITUD_MAXIMA_NOMBRE ? copia : copia.substring(0, LONGITUD_MAXIMA_NOMBRE);
    }
}
