package com.nutrisport.catalogo.servicio;

import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.dto.FuenteResponse;
import com.nutrisport.catalogo.dto.ProductoResponse;
import com.nutrisport.catalogo.dto.ProductoResumenResponse;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.repositorio.AlimentoFuenteRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.integracion.comun.ProveedorExterno;
import com.nutrisport.integracion.openfoodfacts.OpenFoodFactsServicio;
import com.nutrisport.integracion.openfoodfacts.ProductoExternoDto;
import com.nutrisport.integracion.openfoodfacts.TablaAlergenosOff;
import com.nutrisport.perfil.entidad.Alergeno;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Productos comerciales (§7.3, Ampliación): ficha por código de barras con el catálogo como caché de Open Food
 * Facts, y búsqueda bajo demanda en Open Food Facts.
 * <p>
 * Las llamadas al proveedor no se hacen dentro de una transacción propia: la búsqueda en el catálogo y la
 * construcción de la respuesta usan transacciones cortas de solo lectura, y la normalización tiene la suya.
 * Si se llama desde un servicio transaccional (p. ej. al registrar una comida por código), todo se une a la
 * transacción de ese servicio.
 */
@Service
public class ProductoServicio {

    /** Cabecera que avisa de que la búsqueda de productos comerciales no está disponible. */
    public static final String CABECERA_NO_DISPONIBLE = "X-Proveedor-No-Disponible";

    static final Duration VIGENCIA_COPIA = Duration.ofDays(30);
    /** Última página (desde 0) que admite el proveedor (50 páginas de 20). */
    static final int PAGINA_MAXIMA = 49;
    static final String ATRIBUCION_OFF = "Datos de Open Food Facts (ODbL) · openfoodfacts.org";

    private static final Pattern CODIGO = Pattern.compile("\\d{8,14}");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADO = "No encontramos este producto";

    /** Resultado de una búsqueda: si el proveedor no estaba disponible, la lista va vacía. */
    public record Busqueda(List<ProductoResumenResponse> productos, boolean proveedorDisponible) {
    }

    private final OpenFoodFactsServicio openFoodFacts;
    private final NormalizacionAlimentosServicio normalizacion;
    private final AlimentoRepositorio alimentoRepositorio;
    private final AlimentoFuenteRepositorio fuenteRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final TransactionTemplate lectura;
    private final Clock reloj;

    public ProductoServicio(OpenFoodFactsServicio openFoodFacts, NormalizacionAlimentosServicio normalizacion,
            AlimentoRepositorio alimentoRepositorio, AlimentoFuenteRepositorio fuenteRepositorio,
            SeguridadUtils seguridadUtils, PlatformTransactionManager transacciones, Clock reloj) {
        this.openFoodFacts = openFoodFacts;
        this.normalizacion = normalizacion;
        this.alimentoRepositorio = alimentoRepositorio;
        this.fuenteRepositorio = fuenteRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------------------
    // Ficha por código de barras
    // ---------------------------------------------------------------------------------------------

    /**
     * Ficha del producto con ese código:
     * <ol>
     * <li>Si está en el catálogo y su copia de Open Food Facts tiene menos de 30 días, la devuelve sin llamar al
     * proveedor.</li>
     * <li>Si no, lo pide a Open Food Facts y lo guarda (o actualiza) en el catálogo. Si el producto tiene la
     * información nutricional incompleta, se devuelve sin guardar, sin {@code id} y con
     * {@code nutricionCompleta = false}.</li>
     * <li>Si el proveedor no está disponible o ya no lo tiene, devuelve la copia del catálogo aunque sea
     * antigua.</li>
     * </ol>
     *
     * @throws PeticionInvalidaException      400 si el código no tiene entre 8 y 14 dígitos
     * @throws RecursoNoEncontradoException   404 NO_ENCONTRADO si no está ni en el catálogo ni en el proveedor
     * @throws ProveedorNoDisponibleException 503 si el proveedor falla y no hay copia en el catálogo
     */
    public ProductoResponse porCodigo(String codigo) {
        String limpio = validarCodigo(codigo);
        UUID usuarioId = seguridadUtils.usuarioActualId();

        Local local = lectura.execute(estado -> buscarLocal(limpio, usuarioId));
        if (local != null && local.vigente(OffsetDateTime.now(reloj).minus(VIGENCIA_COPIA))) {
            return local.respuesta();
        }

        Optional<ProductoExternoDto> externo;
        try {
            externo = openFoodFacts.buscarPorCodigo(limpio);
        } catch (ProveedorNoDisponibleException e) {
            if (local != null) {
                return local.respuesta();
            }
            throw e;
        }

        if (externo.isEmpty()) {
            if (local != null) {
                return local.respuesta();
            }
            throw new RecursoNoEncontradoException(NO_ENCONTRADO);
        }
        ProductoExternoDto producto = externo.get();
        // Se comprueba antes de normalizar: así una nutrición incompleta no deja una transacción marcada como
        // fallida cuando quien llama es un servicio transaccional
        if (!NormalizacionAlimentosServicio.nutricionValida(producto)) {
            return local != null ? local.respuesta() : sinGuardar(producto);
        }
        UUID id = normalizacion.desdeOpenFoodFacts(producto).getId();
        return lectura.execute(estado -> {
            Alimento alimento = alimentoRepositorio.findById(id).orElseThrow();
            Optional<AlimentoFuente> fuente = fuenteRepositorio
                    .findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, limpio);
            return guardado(alimento, fuente.orElse(null));
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda bajo demanda
    // ---------------------------------------------------------------------------------------------

    /**
     * Busca productos comerciales en Open Food Facts (solo cuando el usuario lo pide, nunca mientras escribe).
     * Los resultados no se guardan; los que ya están en el catálogo llevan su {@code id}. Si el proveedor no
     * está disponible, devuelve una lista vacía y {@code proveedorDisponible = false}.
     *
     * @param pagina desde 0, como el resto de la API
     * @throws PeticionInvalidaException 400 si el texto o la página no son válidos
     */
    public Busqueda buscar(String texto, int pagina) {
        if (pagina < 0 || pagina > PAGINA_MAXIMA) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS,
                    List.of(new ErrorCampo("page", "La página debe estar entre 0 y " + PAGINA_MAXIMA)));
        }
        List<ProductoExternoDto> encontrados;
        try {
            // Search-a-licious numera las páginas desde 1
            encontrados = openFoodFacts.buscarPorTexto(texto, pagina + 1);
        } catch (ProveedorNoDisponibleException e) {
            return new Busqueda(List.of(), false);
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Map<String, UUID> enCatalogo = lectura.execute(estado -> alimentoRepositorio
                .findByCodigoBarrasIn(encontrados.stream().map(ProductoExternoDto::codigo).toList()).stream()
                .filter(alimento -> alimento.esVisiblePara(usuarioId))
                .collect(Collectors.toMap(Alimento::getCodigoBarras, Alimento::getId)));
        List<ProductoResumenResponse> productos = encontrados.stream()
                .map(producto -> new ProductoResumenResponse(
                        enCatalogo.get(producto.codigo()),
                        producto.codigo(),
                        producto.nombre(),
                        producto.marca(),
                        producto.imagenUrl(),
                        producto.kcal100(),
                        NormalizacionAlimentosServicio.nutricionValida(producto),
                        fuenteOff(producto.codigo())))
                .toList();
        return new Busqueda(productos, true);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Copia del catálogo y su procedencia en Open Food Facts (nula si es un alimento de otra fuente). */
    private record Local(ProductoResponse respuesta, OffsetDateTime sincronizadoEn) {

        boolean vigente(OffsetDateTime limite) {
            return sincronizadoEn != null && sincronizadoEn.isAfter(limite);
        }
    }

    /**
     * Busca por procedencia en Open Food Facts y, si no, por código de barras, entre los alimentos que el
     * usuario puede ver. Construye la respuesta dentro de la transacción.
     */
    private Local buscarLocal(String codigo, UUID usuarioId) {
        AlimentoFuente fuente = fuenteRepositorio
                .findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, codigo)
                .orElse(null);
        Alimento alimento = fuente != null
                ? fuente.getAlimento()
                : alimentoRepositorio.findByCodigoBarras(codigo).orElse(null);
        if (alimento == null || !alimento.esVisiblePara(usuarioId)) {
            return null;
        }
        return new Local(guardado(alimento, fuente), fuente == null ? null : fuente.getSincronizadoEn());
    }

    /** Ficha de un alimento del catálogo; sus nutrientes obligatorios siempre están completos. */
    private static ProductoResponse guardado(Alimento alimento, AlimentoFuente fuente) {
        FuenteResponse procedencia = fuente != null
                ? new FuenteResponse(FuenteDato.OPEN_FOOD_FACTS.name(), fuente.getUrl(), fuente.getLicencia(),
                        ATRIBUCION_OFF)
                : new FuenteResponse(alimento.getFuente().name(), null, null, null);
        return new ProductoResponse(
                alimento.getId(),
                alimento.getCodigoBarras(),
                alimento.getNombre(),
                alimento.getMarca(),
                alimento.getImagenUrl(),
                alimento.getUnidadBase(),
                new ProductoResponse.Por100(alimento.getKcal100(), alimento.getProteinas100(),
                        alimento.getCarbohidratos100(), alimento.getGrasas100(), alimento.getAzucares100(),
                        alimento.getSaturadas100(), alimento.getFibra100(), alimento.getSal100()),
                alimento.getRacionGramos() == null ? null
                        : new ProductoResponse.Racion(alimento.getRacionGramos(), alimento.getRacionDescripcion()),
                alimento.getIngredientesTexto(),
                alimento.getAlergenosContiene(),
                alimento.getAlergenosTrazas(),
                alimento.isAlergenosRevisados(),
                true,
                procedencia);
    }

    /** Ficha de un producto que no se ha podido guardar (información nutricional incompleta): sin id. */
    private static ProductoResponse sinGuardar(ProductoExternoDto producto) {
        Set<Alergeno> contiene = TablaAlergenosOff.traducir(producto.alergenosTags());
        Set<Alergeno> trazas = TablaAlergenosOff.traducir(producto.trazasTags());
        trazas.removeAll(contiene);
        Function<Set<Alergeno>, List<String>> nombres = alergenos -> alergenos.stream().map(Enum::name).toList();
        return new ProductoResponse(
                null,
                producto.codigo(),
                producto.nombre(),
                producto.marca(),
                producto.imagenUrl(),
                producto.unidadBase(),
                new ProductoResponse.Por100(producto.kcal100(), producto.proteinas100(), producto.carbohidratos100(),
                        producto.grasas100(), producto.azucares100(), producto.saturadas100(), producto.fibra100(),
                        producto.sal100()),
                producto.racionGramos() == null ? null
                        : new ProductoResponse.Racion(producto.racionGramos(), producto.racionDescripcion()),
                producto.ingredientesTexto(),
                nombres.apply(contiene),
                nombres.apply(trazas),
                false,
                false,
                fuenteOff(producto.codigo()));
    }

    private static FuenteResponse fuenteOff(String codigo) {
        return new FuenteResponse(ProveedorExterno.OPEN_FOOD_FACTS.name(),
                NormalizacionAlimentosServicio.URL_FICHA_OFF + codigo, NormalizacionAlimentosServicio.LICENCIA_OFF,
                ATRIBUCION_OFF);
    }

    private static String validarCodigo(String codigo) {
        String limpio = codigo == null ? "" : codigo.strip();
        if (!CODIGO.matcher(limpio).matches()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS,
                    List.of(new ErrorCampo("codigoBarras", "El código de barras debe tener entre 8 y 14 dígitos")));
        }
        return limpio;
    }
}
