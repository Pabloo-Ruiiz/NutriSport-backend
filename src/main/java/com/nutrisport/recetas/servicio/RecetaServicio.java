package com.nutrisport.recetas.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.dto.FuenteResponse;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.integracion.themealdb.IngredienteExternoDto;
import com.nutrisport.integracion.themealdb.RecetaExternaDto;
import com.nutrisport.integracion.themealdb.RecetaResumenExternaDto;
import com.nutrisport.integracion.themealdb.TablaCategoriasMealDb;
import com.nutrisport.integracion.themealdb.TheMealDbServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.dto.BusquedaRecetasResponse;
import com.nutrisport.recetas.dto.IngredienteRecetaResponse;
import com.nutrisport.recetas.dto.NutricionRacion;
import com.nutrisport.recetas.dto.RecetaResponse;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.PasoReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaFavorita;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaFavoritaRepositorio;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Catálogo de recetas (§3.D y §7.3, Ampliación): búsqueda en el catálogo y en TheMealDB, importación de recetas
 * de TheMealDB, detalle, relacionadas y favoritas.
 * <p>
 * Las llamadas a TheMealDB se hacen fuera de cualquier transacción; la base de datos se usa en transacciones
 * cortas. Las recetas de TheMealDB llevan siempre su atribución ({@code fuente}).
 */
@Service
public class RecetaServicio {

    /** Página pública de una receta en TheMealDB (comprobada en la web: /meal/52850 carga "Chicken Couscous"). */
    static final String URL_RECETA_THEMEALDB = "https://www.themealdb.com/meal/";
    static final String ATRIBUCION_THEMEALDB = "Receta e imagen: TheMealDB";
    static final String PENDIENTE = "PENDIENTE";
    static final int MAXIMO_RELACIONADAS = 6;
    static final int MAXIMO_EXTERNAS = 20;
    static final int TAMANO_MAXIMO_PAGINA = 50;
    static final int MAXIMO_RACIONES = 20;
    private static final int LONGITUD_MAXIMA_TEXTO = 60;
    private static final int MAXIMO_ETIQUETAS = 10;
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADA = "La receta no existe";

    /** Valores del parámetro {@code origen} de la búsqueda. */
    enum Origen {
        TODAS, PROPIA, THEMEALDB
    }

    private final RecetaRepositorio recetaRepositorio;
    private final RecetaFavoritaRepositorio favoritaRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final TheMealDbServicio theMealDb;
    private final RestriccionesServicio restriccionesServicio;
    private final SeguridadUtils seguridadUtils;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final Clock reloj;

    public RecetaServicio(RecetaRepositorio recetaRepositorio, RecetaFavoritaRepositorio favoritaRepositorio,
            CategoriaRepositorio categoriaRepositorio, UsuarioRepositorio usuarioRepositorio,
            TheMealDbServicio theMealDb, RestriccionesServicio restriccionesServicio, SeguridadUtils seguridadUtils,
            PlatformTransactionManager transacciones, Clock reloj) {
        this.recetaRepositorio = recetaRepositorio;
        this.favoritaRepositorio = favoritaRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.theMealDb = theMealDb;
        this.restriccionesServicio = restriccionesServicio;
        this.seguridadUtils = seguridadUtils;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda
    // ---------------------------------------------------------------------------------------------

    /**
     * Recetas del catálogo (públicas publicadas y propias) y, en la primera página, las de TheMealDB que aún no
     * están importadas.
     * <p>
     * TheMealDB se consulta si {@code origen} es "themealdb" o "todas" (por defecto) y hay un filtro que TheMealDB
     * entiende: el texto ({@code search.php}) o, si no, el ingrediente, la categoría (si tiene equivalente) o el
     * área ({@code filter.php}). No se consulta si se filtra por franja, kcal o proteína: TheMealDB no tiene esos
     * datos y los filtros nutricionales solo se aplican a recetas con cálculo COMPLETO o APROXIMADO. Las recetas
     * de TheMealDB que ya están importadas (aunque no estén publicadas) aparecen con su {@code id}.
     *
     * @param origen "todas", "propia" o "themealdb" (sin distinguir mayúsculas); nulo = "todas"
     */
    public BusquedaRecetasResponse buscar(String q, String ingrediente, UUID categoriaId, String area, String origen,
            TipoComida franja, BigDecimal maxKcal, BigDecimal minProteina, Pageable paginacion) {
        Origen filtroOrigen = origen(origen);
        String texto = normalizar("q", q);
        String textoIngrediente = normalizar("ingrediente", ingrediente);
        String textoArea = area == null || area.isBlank() ? "" : validarLongitud("area", area).toLowerCase(Locale.ROOT);
        if (paginacion.getPageSize() > TAMANO_MAXIMO_PAGINA) {
            throw invalida("size", "El tamaño de página no puede superar " + TAMANO_MAXIMO_PAGINA);
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize());

        PaginaResponse<RecetaResumenResponse> catalogo = lectura.execute(estado -> {
            Page<Receta> recetas = recetaRepositorio.buscarCatalogo(texto, categoriaId, textoArea,
                    filtroOrigen == Origen.TODAS ? "" : filtroOrigen.name(), franja == null ? "" : franja.name(),
                    textoIngrediente, maxKcal, minProteina, usuarioId, pagina);
            return PaginaResponse.de(recetas, resumidor(usuarioId, recetas.getContent()));
        });

        boolean consultarTheMealDb = filtroOrigen != Origen.PROPIA && pagina.getPageNumber() == 0
                && franja == null && maxKcal == null && minProteina == null;
        if (!consultarTheMealDb) {
            return new BusquedaRecetasResponse(catalogo, List.of(), true);
        }
        List<RecetaResumenExternaDto> externas;
        try {
            externas = buscarEnTheMealDb(q, ingrediente, categoriaId, area);
        } catch (ProveedorNoDisponibleException e) {
            return new BusquedaRecetasResponse(catalogo, List.of(), false);
        }
        return new BusquedaRecetasResponse(catalogo, externas(externas, catalogo, usuarioId), true);
    }

    // ---------------------------------------------------------------------------------------------
    // Importación de TheMealDB
    // ---------------------------------------------------------------------------------------------

    /**
     * Detalle de una receta de TheMealDB con su {@code id} propio. Si ya está importada se devuelve sin llamar a
     * TheMealDB; si no, se importa: idioma "en", 4 raciones supuestas, sin publicar, categoría equivalente (o
     * "Otras"), instrucciones partidas por líneas e ingredientes sin emparejar (SIN_DATOS).
     *
     * @throws RecursoNoEncontradoException   404 si TheMealDB no tiene esa receta
     * @throws ProveedorNoDisponibleException 503 si hay que importarla y TheMealDB no responde
     */
    public RecetaResponse importarDeTheMealDb(String idMeal) {
        UUID existente = idImportada(idMeal);
        if (existente == null) {
            RecetaExternaDto externa = theMealDb.porId(idMeal)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe en TheMealDB"));
            try {
                existente = escritura.execute(estado -> recetaRepositorio.saveAndFlush(nuevaReceta(externa)).getId());
            } catch (DataIntegrityViolationException e) {
                // Otra petición la ha importado a la vez (UNIQUE origen + referencia_externa)
                existente = idImportada(externa.idMeal());
                if (existente == null) {
                    throw e;
                }
            }
            // TODO A3: calcular nutrientes, alérgenos y aptitudes con CalculoNutricionalServicio
        }
        return detalle(existente, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Detalle, relacionadas y favoritas
    // ---------------------------------------------------------------------------------------------

    /**
     * Detalle de una receta visible para el usuario, con las cantidades de los ingredientes para
     * {@code raciones} (las de la receta si es nulo, de 1 a 20). Los nutrientes son siempre por ración.
     */
    public RecetaResponse detalle(UUID id, Integer raciones) {
        if (raciones != null && (raciones < 1 || raciones > MAXIMO_RACIONES)) {
            throw invalida("raciones", "Las raciones deben estar entre 1 y " + MAXIMO_RACIONES);
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return lectura.execute(estado -> {
            Receta receta = visible(id, usuarioId);
            boolean favorita = favoritaRepositorio.existsById(new RecetaFavorita.Id(usuarioId, receta.getId()));
            return respuesta(receta, raciones != null ? raciones : receta.getRaciones(),
                    restriccionesServicio.estrictasDe(usuarioId), favorita);
        });
    }

    /** Hasta 6 recetas de la misma categoría o con el mismo primer ingrediente. */
    public List<RecetaResumenResponse> relacionadas(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return lectura.execute(estado -> {
            Receta receta = visible(id, usuarioId);
            RecetaIngrediente primero = receta.getIngredientes().isEmpty() ? null : receta.getIngredientes().get(0);
            List<Receta> relacionadas = recetaRepositorio.relacionadas(receta.getId(),
                    receta.getCategoria() == null ? null : receta.getCategoria().getId(),
                    primero == null || primero.getAlimento() == null ? null : primero.getAlimento().getId(),
                    primero == null ? "" : primero.getNombreOriginal().toLowerCase(Locale.ROOT),
                    usuarioId, MAXIMO_RELACIONADAS);
            return relacionadas.stream().map(resumidor(usuarioId, relacionadas)).toList();
        });
    }

    /** Recetas favoritas del usuario que sigue pudiendo ver, las marcadas más recientemente primero. */
    public List<RecetaResumenResponse> favoritas() {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return lectura.execute(estado -> {
            List<Receta> recetas = favoritaRepositorio.findByIdUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
                    .map(RecetaFavorita::getReceta)
                    .filter(receta -> receta.esVisiblePara(usuarioId))
                    .toList();
            return recetas.stream().map(resumidor(usuarioId, recetas)).toList();
        });
    }

    /** Marca la receta como favorita; si ya lo era, no hace nada. 404 si no existe o no es visible. */
    public void marcarFavorita(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        escritura.executeWithoutResult(estado -> {
            Receta receta = visible(id, usuarioId);
            RecetaFavorita.Id clave = new RecetaFavorita.Id(usuarioId, receta.getId());
            if (!favoritaRepositorio.existsById(clave)) {
                favoritaRepositorio.save(new RecetaFavorita(usuarioRepositorio.getReferenceById(usuarioId), receta));
            }
        });
    }

    /** Quita la receta de favoritas; si no lo era, no hace nada. 404 si no existe o no es visible. */
    public void quitarFavorita(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        escritura.executeWithoutResult(estado -> {
            Receta receta = visible(id, usuarioId);
            RecetaFavorita.Id clave = new RecetaFavorita.Id(usuarioId, receta.getId());
            if (favoritaRepositorio.existsById(clave)) {
                favoritaRepositorio.deleteById(clave);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // TheMealDB
    // ---------------------------------------------------------------------------------------------

    /** Consulta TheMealDB con el primer filtro que entiende; vacío si ninguno aplica. */
    private List<RecetaResumenExternaDto> buscarEnTheMealDb(String q, String ingrediente, UUID categoriaId,
            String area) {
        if (q != null && q.strip().length() >= 2) {
            return theMealDb.buscarPorNombre(q).stream()
                    .map(receta -> new RecetaResumenExternaDto(receta.idMeal(), receta.nombre(), receta.imagenUrl()))
                    .toList();
        }
        if (ingrediente != null && !ingrediente.isBlank()) {
            return theMealDb.filtrarPorIngrediente(ingrediente);
        }
        if (categoriaId != null) {
            return TablaCategoriasMealDb.codigoPara(categoriaId).map(theMealDb::filtrarPorCategoria).orElse(List.of());
        }
        if (area != null && !area.isBlank()) {
            return theMealDb.filtrarPorArea(area);
        }
        return List.of();
    }

    /**
     * Resultados de TheMealDB que no están ya en la página del catálogo: los no importados como PENDIENTE y los
     * importados (aunque no estén publicados) con su id y su estado.
     */
    private List<RecetaResumenResponse> externas(List<RecetaResumenExternaDto> externas,
            PaginaResponse<RecetaResumenResponse> catalogo, UUID usuarioId) {
        Set<String> enPagina = catalogo.content().stream()
                .map(RecetaResumenResponse::referenciaExterna)
                .filter(referencia -> referencia != null)
                .collect(Collectors.toSet());
        List<RecetaResumenExternaDto> nuevas = externas.stream()
                .filter(externa -> !enPagina.contains(externa.idMeal()))
                .limit(MAXIMO_EXTERNAS)
                .toList();
        return lectura.execute(estado -> {
            List<Receta> importadas = recetaRepositorio.findByOrigenAndReferenciaExternaIn(OrigenReceta.THEMEALDB,
                    nuevas.stream().map(RecetaResumenExternaDto::idMeal).toList());
            Map<String, Receta> porReferencia = importadas.stream()
                    .collect(Collectors.toMap(Receta::getReferenciaExterna, Function.identity()));
            Function<Receta, RecetaResumenResponse> resumir = resumidor(usuarioId, importadas);
            return nuevas.stream()
                    .map(externa -> porReferencia.containsKey(externa.idMeal())
                            ? resumir.apply(porReferencia.get(externa.idMeal()))
                            : pendiente(externa))
                    .toList();
        });
    }

    private static RecetaResumenResponse pendiente(RecetaResumenExternaDto externa) {
        return new RecetaResumenResponse(null, externa.idMeal(), OrigenReceta.THEMEALDB, externa.nombre(), null, null,
                externa.imagenUrl(), null, PENDIENTE, false, false, false, fuenteTheMealDb(externa.idMeal()));
    }

    private UUID idImportada(String idMeal) {
        return lectura.execute(estado -> recetaRepositorio
                .findByOrigenAndReferenciaExterna(OrigenReceta.THEMEALDB, idMeal == null ? "" : idMeal.strip())
                .map(Receta::getId)
                .orElse(null));
    }

    private Receta nuevaReceta(RecetaExternaDto externa) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.THEMEALDB);
        receta.setReferenciaExterna(externa.idMeal());
        receta.setNombre(recortar(externa.nombre(), 150));
        receta.setIdioma("en");
        receta.setCategoriaExterna(recortar(externa.categoria(), 40));
        receta.setArea(recortar(externa.area(), 40));
        receta.setCategoria(categoriaRepositorio.getReferenceById(
                TablaCategoriasMealDb.categoriaUOtras(externa.categoria())));
        receta.setImagenUrl(urlSegura(externa.imagenUrl()));
        receta.setRaciones(Receta.RACIONES_POR_DEFECTO);
        receta.setRacionesSupuestas(true);
        receta.setPublicada(false);
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPasos(pasos(externa.instrucciones()));
        receta.setEtiquetas(externa.etiquetas().stream()
                .map(etiqueta -> recortar(etiqueta.toLowerCase(Locale.ROOT), 40))
                .limit(MAXIMO_ETIQUETAS)
                .toList());
        receta.setSincronizadoEn(OffsetDateTime.now(reloj));
        for (IngredienteExternoDto ingrediente : externa.ingredientes()) {
            receta.anadirIngrediente(new RecetaIngrediente(recortar(ingrediente.nombre(), 100),
                    recortar(ingrediente.medida(), 60)));
        }
        return receta;
    }

    /** Las instrucciones de TheMealDB, un paso por línea no vacía. */
    static List<PasoReceta> pasos(String instrucciones) {
        if (instrucciones == null || instrucciones.isBlank()) {
            return List.of();
        }
        List<PasoReceta> pasos = new ArrayList<>();
        for (String linea : instrucciones.split("\\R")) {
            if (!linea.isBlank()) {
                pasos.add(new PasoReceta(pasos.size() + 1, linea.strip()));
            }
        }
        return pasos;
    }

    // ---------------------------------------------------------------------------------------------
    // Respuestas
    // ---------------------------------------------------------------------------------------------

    /** Convierte recetas de un mismo listado consultando las favoritas y las restricciones una sola vez. */
    private Function<Receta, RecetaResumenResponse> resumidor(UUID usuarioId, List<Receta> recetas) {
        RestriccionesUsuario restricciones = restriccionesServicio.estrictasDe(usuarioId);
        Set<UUID> favoritas = recetas.isEmpty() ? Set.of()
                : favoritaRepositorio.favoritasDe(usuarioId, recetas.stream().map(Receta::getId).toList());
        return receta -> new RecetaResumenResponse(
                receta.getId(),
                receta.getReferenciaExterna(),
                receta.getOrigen(),
                receta.getNombre(),
                receta.getCategoria() == null ? null : receta.getCategoria().getNombre(),
                receta.getArea(),
                receta.getImagenUrl(),
                nutricion(receta),
                receta.getEstadoNutricional().name(),
                receta.isAlergenosVerificados(),
                CompatibilidadReceta.compatible(receta, restricciones),
                favoritas.contains(receta.getId()),
                fuente(receta));
    }

    private static RecetaResponse respuesta(Receta receta, int raciones, RestriccionesUsuario restricciones,
            boolean favorita) {
        BigDecimal factor = BigDecimal.valueOf(raciones)
                .divide(BigDecimal.valueOf(receta.getRaciones()), 6, RoundingMode.HALF_UP);
        List<IngredienteRecetaResponse> ingredientes = receta.getIngredientes().stream()
                .map(ingrediente -> new IngredienteRecetaResponse(
                        ingrediente.getAlimento() != null ? ingrediente.getAlimento().getNombre()
                                : ingrediente.getNombreOriginal(),
                        ingrediente.getNombreOriginal(),
                        ingrediente.getMedidaOriginal(),
                        ingrediente.getCantidadGramos() == null ? null
                                : ingrediente.getCantidadGramos().multiply(factor).setScale(1, RoundingMode.HALF_UP),
                        ingrediente.getEstadoConversion(),
                        ingrediente.getAlimento() == null ? null : ingrediente.getAlimento().getId()))
                .toList();
        return new RecetaResponse(
                receta.getId(),
                receta.getOrigen(),
                receta.getNombre(),
                receta.getDescripcion(),
                receta.getIdioma(),
                receta.getCategoria() == null ? null : receta.getCategoria().getNombre(),
                receta.getArea(),
                receta.getImagenUrl(),
                raciones,
                receta.isRacionesSupuestas(),
                receta.getTiempoPreparacionMin(),
                receta.getDificultad(),
                receta.getFranjasAptas(),
                receta.getEtiquetas(),
                ingredientes,
                receta.getPasos(),
                nutricion(receta),
                receta.getEstadoNutricional(),
                receta.getCompletitudPct(),
                receta.getAlergenosContiene(),
                receta.isAlergenosVerificados(),
                CompatibilidadReceta.compatible(receta, restricciones),
                favorita,
                fuente(receta));
    }

    /** Solo con cálculo COMPLETO o APROXIMADO. */
    private static NutricionRacion nutricion(Receta receta) {
        boolean calculada = receta.getEstadoNutricional() == EstadoNutricional.COMPLETO
                || receta.getEstadoNutricional() == EstadoNutricional.APROXIMADO;
        if (!calculada || receta.getKcalRacion() == null) {
            return null;
        }
        return new NutricionRacion(receta.getKcalRacion(), receta.getProteinaRacion(),
                receta.getCarbohidratosRacion(), receta.getGrasaRacion());
    }

    /** Atribución de las recetas de TheMealDB; nula en las propias. */
    private static FuenteResponse fuente(Receta receta) {
        return receta.getOrigen() == OrigenReceta.THEMEALDB ? fuenteTheMealDb(receta.getReferenciaExterna()) : null;
    }

    static FuenteResponse fuenteTheMealDb(String idMeal) {
        return new FuenteResponse(OrigenReceta.THEMEALDB.name(), URL_RECETA_THEMEALDB + idMeal, null,
                ATRIBUCION_THEMEALDB);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private Receta visible(UUID id, UUID usuarioId) {
        return recetaRepositorio.findWithIngredientesById(id)
                .filter(receta -> receta.esVisiblePara(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    private static Origen origen(String origen) {
        if (origen == null || origen.isBlank()) {
            return Origen.TODAS;
        }
        try {
            return Origen.valueOf(origen.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalida("origen", "Debe ser todas, propia o themealdb");
        }
    }

    /** Texto normalizado para buscar (minúsculas, sin tildes ni comodines); vacío si no hay. */
    private static String normalizar(String campo, String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return TextoNormalizado.paraBusqueda(validarLongitud(campo, texto));
    }

    private static String validarLongitud(String campo, String texto) {
        String limpio = texto.strip();
        if (limpio.length() > LONGITUD_MAXIMA_TEXTO) {
            throw invalida(campo, "No puede superar los " + LONGITUD_MAXIMA_TEXTO + " caracteres");
        }
        return limpio;
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo).strip();
    }

    /** Solo enlaces https que caben en la columna; si no, sin imagen. */
    private static String urlSegura(String url) {
        return url != null && url.startsWith("https://") && url.length() <= 300 ? url : null;
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
