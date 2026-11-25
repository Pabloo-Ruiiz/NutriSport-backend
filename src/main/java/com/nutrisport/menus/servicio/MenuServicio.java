package com.nutrisport.menus.servicio;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.menus.dto.ActualizarMenuRequest;
import com.nutrisport.menus.dto.ComidaPendienteResponse;
import com.nutrisport.menus.dto.ComidaPlanificadaResponse;
import com.nutrisport.menus.dto.DiaMenuResponse;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.dto.MenuResumenResponse;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.repositorio.ComidaPlanificadaRepositorio;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.servicio.RecetaServicio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menús del usuario autenticado (§3.D y §7.5, Ampliación): listado, detalle por días frente al objetivo de cada día,
 * cambios y borrado. Los menús de otro usuario responden 404.
 * <p>
 * El detalle no es de solo lectura: el objetivo de un día se calcula y se guarda la primera vez que se pide
 * ({@link ObjetivoDiarioServicio#obtener}).
 */
@Service
@Transactional
public class MenuServicio {

    static final int TAMANO_MAXIMO_PAGINA = 50;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final String NO_ENCONTRADO = "El menú no existe";

    private final MenuSemanalRepositorio menuRepositorio;
    private final ComidaPlanificadaRepositorio comidaRepositorio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final RecetaServicio recetaServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;

    public MenuServicio(MenuSemanalRepositorio menuRepositorio, ComidaPlanificadaRepositorio comidaRepositorio,
            ObjetivoDiarioServicio objetivoDiarioServicio,
            PerfilDeportivoRepositorio perfilRepositorio, EntrenamientoRepositorio entrenamientoRepositorio,
            RecetaServicio recetaServicio, SeguridadUtils seguridadUtils, Clock reloj) {
        this.menuRepositorio = menuRepositorio;
        this.comidaRepositorio = comidaRepositorio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.perfilRepositorio = perfilRepositorio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.recetaServicio = recetaServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    /** Menús del usuario, el más reciente primero; como mucho 50 por página. */
    @Transactional(readOnly = true)
    public PaginaResponse<MenuResumenResponse> listar(Boolean favorito, LocalDate desde, Pageable paginacion) {
        if (paginacion.getPageSize() > TAMANO_MAXIMO_PAGINA) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos", List.of(new ErrorCampo("size",
                    "El tamaño de página no puede superar " + TAMANO_MAXIMO_PAGINA)));
        }
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize());
        return PaginaResponse.de(menuRepositorio.buscar(seguridadUtils.usuarioActualId(), favorito, desde, pagina),
                menu -> new MenuResumenResponse(menu.getId(), menu.getNombre(), menu.getTipo(), menu.getFechaInicio(),
                        menu.getFechaFin(), menu.isFavorito(), menu.getEstado(), menu.isGeneradoAutomaticamente(),
                        menu.getComidas().size()));
    }

    /**
     * Detalle del menú: todos los días del rango con sus comidas, lo planificado frente al objetivo del día y si es día
     * de entreno. Sin perfil no hay objetivo (el día va sin él en lugar de fallar).
     */
    public MenuResponse obtener(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return respuesta(propio(id, usuarioId), usuarioId);
    }

    /** Renombra, marca como favorito o cambia de estado; los campos nulos no cambian. */
    public MenuResponse actualizar(UUID id, ActualizarMenuRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = propio(id, usuarioId);
        if (peticion.nombre() != null) {
            menu.setNombre(peticion.nombre().strip());
        }
        if (peticion.favorito() != null) {
            menu.setFavorito(peticion.favorito());
        }
        if (peticion.estado() != null) {
            menu.setEstado(peticion.estado());
        }
        return respuesta(menu, usuarioId);
    }

    /** Borra el menú con sus comidas planificadas (las comidas ya registradas no se borran). */
    public void eliminar(UUID id) {
        menuRepositorio.delete(propio(id, seguridadUtils.usuarioActualId()));
    }

    /**
     * Comidas planificadas aún no consumidas de ese día en los menús ACTIVO del usuario (resumen diario, §3.F).
     */
    @Transactional(readOnly = true)
    public List<ComidaPendienteResponse> pendientesDelDia(UUID usuarioId, LocalDate fecha) {
        List<ComidaPlanificada> pendientes = comidaRepositorio.pendientesDelDia(usuarioId, fecha);
        Map<UUID, RecetaResumenResponse> resumenes = recetaServicio.resumenes(usuarioId,
                pendientes.stream().map(ComidaPlanificada::getReceta).distinct().toList());
        return pendientes.stream()
                .map(comida -> new ComidaPendienteResponse(comida.getMenu().getId(), comida.getMenu().getNombre(),
                        comida.getId(), comida.getFecha(), comida.getTipoComida(),
                        resumenes.get(comida.getReceta().getId()), comida.getRaciones(), nutrientes(comida)))
                .toList();
    }

    /**
     * Comidas planificadas de ese día en los menús ACTIVO del usuario, consumidas o no (herramienta del asistente).
     * No depende del contexto de seguridad.
     */
    @Transactional(readOnly = true)
    public List<ComidaPlanificadaResponse> comidasDelDia(UUID usuarioId, LocalDate fecha) {
        List<ComidaPlanificada> comidas = comidaRepositorio.delDia(usuarioId, fecha);
        Map<UUID, RecetaResumenResponse> resumenes = recetaServicio.resumenes(usuarioId,
                comidas.stream().map(ComidaPlanificada::getReceta).distinct().toList());
        return comidas.stream()
                .map(comida -> new ComidaPlanificadaResponse(comida.getId(), comida.getFecha(),
                        comida.getTipoComida(), resumenes.get(comida.getReceta().getId()), comida.getRaciones(),
                        nutrientes(comida), comida.isBloqueada(), comida.isConsumida()))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------

    MenuSemanal propio(UUID id, UUID usuarioId) {
        return menuRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    /** Menú completo con sus días (dentro de una transacción de escritura: puede guardar objetivos). */
    MenuResponse respuesta(MenuSemanal menu, UUID usuarioId) {
        List<Receta> recetas = menu.getComidas().stream().map(ComidaPlanificada::getReceta).distinct().toList();
        Map<UUID, RecetaResumenResponse> resumenes = recetaServicio.resumenes(usuarioId, recetas);
        Map<LocalDate, List<ComidaPlanificada>> porDia = menu.getComidas().stream()
                .collect(Collectors.groupingBy(ComidaPlanificada::getFecha));
        boolean conPerfil = perfilRepositorio.findByUsuarioId(usuarioId).isPresent();
        Set<LocalDate> diasEntreno = diasDeEntreno(usuarioId, menu.getFechaInicio(), menu.getFechaFin());

        List<DiaMenuResponse> dias = new ArrayList<>();
        Nutrientes totalPlanificado = Nutrientes.CERO;
        BigDecimal kcalObjetivos = BigDecimal.ZERO;
        BigDecimal kcalConObjetivo = BigDecimal.ZERO;
        for (LocalDate fecha = menu.getFechaInicio(); !fecha.isAfter(menu.getFechaFin()); fecha = fecha.plusDays(1)) {
            List<ComidaPlanificadaResponse> comidas = porDia.getOrDefault(fecha, List.of()).stream()
                    .map(comida -> new ComidaPlanificadaResponse(comida.getId(), comida.getFecha(),
                            comida.getTipoComida(), resumenes.get(comida.getReceta().getId()), comida.getRaciones(),
                            nutrientes(comida), comida.isBloqueada(), comida.isConsumida()))
                    .toList();
            Nutrientes planificado = comidas.stream()
                    .map(ComidaPlanificadaResponse::nutrientes)
                    .filter(n -> n != null)
                    .reduce(Nutrientes.CERO, Nutrientes::sumar)
                    .redondear();
            ObjetivoDiarioResponse objetivo = conPerfil ? objetivoDiarioServicio.obtener(usuarioId, fecha) : null;
            int porcentaje = 0;
            if (objetivo != null && objetivo.total().kcal().signum() > 0) {
                porcentaje = planificado.kcal().multiply(CIEN)
                        .divide(objetivo.total().kcal(), 0, RoundingMode.HALF_UP).intValue();
                kcalObjetivos = kcalObjetivos.add(objetivo.total().kcal());
                kcalConObjetivo = kcalConObjetivo.add(planificado.kcal());
            }
            totalPlanificado = totalPlanificado.sumar(planificado);
            dias.add(new DiaMenuResponse(fecha, objetivo, planificado, porcentaje, diasEntreno.contains(fecha), comidas));
        }

        BigDecimal numeroDias = BigDecimal.valueOf(dias.size());
        MenuResponse.Resumen resumen = new MenuResponse.Resumen(
                totalPlanificado.kcal().divide(numeroDias, 1, RoundingMode.HALF_UP),
                kcalObjetivos.signum() == 0 ? null
                        : kcalConObjetivo.multiply(CIEN).divide(kcalObjetivos, 0, RoundingMode.HALF_UP).intValue(),
                totalPlanificado.proteina().divide(numeroDias, 1, RoundingMode.HALF_UP));
        return new MenuResponse(menu.getId(), menu.getNombre(), menu.getTipo(), menu.getFechaInicio(),
                menu.getFechaFin(), menu.isFavorito(), menu.getEstado(), menu.isGeneradoAutomaticamente(),
                menu.getExplicacion(), dias, resumen);
    }

    /** Nutrientes de una ración de la receta × raciones; nulos si la receta no tiene cálculo utilizable. */
    static Nutrientes nutrientes(ComidaPlanificada comida) {
        Receta receta = comida.getReceta();
        boolean calculada = receta.getEstadoNutricional() == EstadoNutricional.COMPLETO
                || receta.getEstadoNutricional() == EstadoNutricional.APROXIMADO;
        if (!calculada || receta.getKcalRacion() == null) {
            return null;
        }
        return new Nutrientes(receta.getKcalRacion(), receta.getProteinaRacion(), receta.getCarbohidratosRacion(),
                receta.getGrasaRacion())
                .escalar(comida.getRaciones())
                .redondear();
    }

    /** Días (en la zona horaria de la aplicación) con entrenamientos planificados o realizados. */
    Set<LocalDate> diasDeEntreno(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        ZoneId zona = reloj.getZone();
        return entrenamientoRepositorio.buscarEntre(usuarioId, desde.atStartOfDay(zona).toOffsetDateTime(),
                        hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime()).stream()
                .filter(entrenamiento -> entrenamiento.getEstado() != EstadoEntrenamiento.OMITIDO)
                .map(entrenamiento -> entrenamiento.getFechaHora().atZoneSameInstant(zona).toLocalDate())
                .collect(Collectors.toSet());
    }
}
