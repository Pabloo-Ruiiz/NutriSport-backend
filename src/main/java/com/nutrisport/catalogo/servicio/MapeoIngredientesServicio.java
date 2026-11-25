package com.nutrisport.catalogo.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.MapeoIngrediente;
import com.nutrisport.catalogo.entidad.MetodoMapeo;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.MapeoIngredienteRepositorio;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.comun.util.SimilitudTexto;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.integracion.usda.AlimentoUsdaDto;
import com.nutrisport.integracion.usda.DetalleUsdaDto;
import com.nutrisport.integracion.usda.FoodDataCentralServicio;
import com.nutrisport.integracion.usda.PorcionUsdaDto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Empareja los nombres de ingredientes (de TheMealDB, en inglés) con alimentos del catálogo, que pueden venir de
 * USDA (docs/API_INTEGRATIONS.md §6.2 y §7.1, paso 2). Cada nombre se resuelve una sola vez y se guarda en
 * {@link MapeoIngrediente}, para no repetir búsquedas en USDA y para que el administrador pueda revisarlo.
 * <p>
 * Las llamadas a USDA se hacen fuera de cualquier transacción; la base de datos se usa en transacciones cortas.
 */
@Service
public class MapeoIngredientesServicio {

    private static final Logger LOG = LoggerFactory.getLogger(MapeoIngredientesServicio.class);

    /** Por debajo, el emparejamiento automático queda pendiente de revisión y no se usa. */
    public static final BigDecimal UMBRAL_CONFIANZA = new BigDecimal("0.60");
    /** Con una coincidencia así en el catálogo no se consulta USDA (ahorra cupo). */
    static final BigDecimal CONFIANZA_SUFICIENTE_CATALOGO = new BigDecimal("0.90");
    static final int CANDIDATOS_CATALOGO = 5;
    static final int LONGITUD_NOMBRE = 100;

    /** Una coincidencia posible: un alimento del catálogo o uno de USDA aún no importado. */
    private record Candidato(Alimento alimento, AlimentoUsdaDto usda, BigDecimal confianza) {
    }

    private final MapeoIngredienteRepositorio mapeoRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final FoodDataCentralServicio usda;
    private final NormalizacionAlimentosServicio normalizacion;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public MapeoIngredientesServicio(MapeoIngredienteRepositorio mapeoRepositorio,
            AlimentoRepositorio alimentoRepositorio, FoodDataCentralServicio usda,
            NormalizacionAlimentosServicio normalizacion, PlatformTransactionManager transacciones) {
        this.mapeoRepositorio = mapeoRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.usda = usda;
        this.normalizacion = normalizacion;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /**
     * Alimento del catálogo para un ingrediente ("Chicken Breast"), o vacío si no hay una coincidencia fiable:
     * <ol>
     * <li>Si el nombre normalizado ya tiene un emparejamiento, se usa (vacío si está pendiente).</li>
     * <li>Si no, se buscan candidatos en el catálogo (alimentos públicos) y, si ninguno es casi exacto, en USDA
     * (Foundation y SR Legacy, con la nutrición completa). Gana el de mayor similitud de texto.</li>
     * <li>Se guarda el emparejamiento (AUTOMATICO, sin revisar). Si el ganador es de USDA y su confianza llega al
     * umbral, se importa al catálogo con sus medidas caseras.</li>
     * <li>Con una confianza menor de 0,6 el emparejamiento queda pendiente de revisión y se devuelve vacío.</li>
     * </ol>
     * Si USDA no está disponible (sin clave, sin cupo o caído) y el catálogo no basta, no se guarda nada: se
     * volverá a intentar la próxima vez.
     */
    public Optional<Alimento> resolver(String nombreOriginal) {
        String clave = clave(nombreOriginal);
        if (clave.isEmpty()) {
            return Optional.empty();
        }
        Optional<MapeoIngrediente> existente = lectura.execute(estado -> mapeoRepositorio.findByNombreNormalizado(clave));
        if (existente.isPresent()) {
            return utilizable(existente.get());
        }

        Candidato mejor = mejorDelCatalogo(nombreOriginal);
        boolean usdaFallo = false;
        if (mejor == null || mejor.confianza().compareTo(CONFIANZA_SUFICIENTE_CATALOGO) < 0) {
            try {
                Candidato deUsda = mejorDeUsda(nombreOriginal);
                if (deUsda != null && (mejor == null || deUsda.confianza().compareTo(mejor.confianza()) > 0)) {
                    mejor = deUsda;
                }
            } catch (ProveedorNoDisponibleException e) {
                usdaFallo = true;
            }
        }

        boolean fiable = mejor != null && mejor.confianza().compareTo(UMBRAL_CONFIANZA) >= 0;
        if (!fiable && usdaFallo) {
            return Optional.empty();
        }
        Alimento alimento = mejor == null ? null : mejor.alimento();
        if (fiable && mejor.usda() != null) {
            alimento = importarDeUsda(mejor.usda());
            if (alimento == null) {
                // No se ha podido importar (USDA caído al pedir el detalle o nutrición no válida): sin guardar
                return Optional.empty();
            }
        }
        guardar(clave, alimento, mejor == null ? null : mejor.confianza());
        return fiable ? Optional.of(alimento) : Optional.empty();
    }

    /** Nombre normalizado del ingrediente (minúsculas, sin tildes), como mucho 100 caracteres. */
    static String clave(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        String normalizado = TextoNormalizado.normalizar(nombreOriginal).strip().replaceAll("\\s+", " ");
        return normalizado.length() <= LONGITUD_NOMBRE ? normalizado : normalizado.substring(0, LONGITUD_NOMBRE).strip();
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** El alimento de un emparejamiento guardado, si se puede usar: manual, revisado o con confianza suficiente. */
    private static Optional<Alimento> utilizable(MapeoIngrediente mapeo) {
        boolean aceptado = mapeo.getMetodo() == MetodoMapeo.MANUAL || mapeo.isRevisado()
                || (mapeo.getConfianza() != null && mapeo.getConfianza().compareTo(UMBRAL_CONFIANZA) >= 0);
        return aceptado ? Optional.ofNullable(mapeo.getAlimento()) : Optional.empty();
    }

    private Candidato mejorDelCatalogo(String nombreOriginal) {
        String consulta = TextoNormalizado.paraBusqueda(nombreOriginal);
        if (consulta.length() < 2) {
            return null;
        }
        return lectura.execute(estado -> alimentoRepositorio
                .buscar(consulta, null, (UUID) null, PageRequest.of(0, CANDIDATOS_CATALOGO))
                .getContent().stream()
                .map(alimento -> new Candidato(alimento, null,
                        SimilitudTexto.confianza(nombreOriginal, alimento.getNombre())))
                .max(Comparator.comparing(Candidato::confianza))
                .orElse(null));
    }

    /** Mejor resultado de USDA con la nutrición completa; nulo si no hay clave o ninguno vale. */
    private Candidato mejorDeUsda(String nombreOriginal) {
        if (!usda.configurado()) {
            throw new ProveedorNoDisponibleException(60);
        }
        return usda.buscar(nombreOriginal).stream()
                .filter(NormalizacionAlimentosServicio::nutricionValida)
                .map(alimento -> new Candidato(null, alimento,
                        SimilitudTexto.confianza(nombreOriginal, alimento.descripcion())))
                .max(Comparator.comparing(Candidato::confianza))
                .orElse(null);
    }

    /** Importa al catálogo el alimento de USDA con las medidas caseras del detalle; nulo si no se puede. */
    private Alimento importarDeUsda(AlimentoUsdaDto encontrado) {
        AlimentoUsdaDto datos = encontrado;
        List<PorcionUsdaDto> porciones = List.of();
        try {
            Optional<DetalleUsdaDto> detalle = usda.detalle(encontrado.fdcId());
            if (detalle.isPresent()) {
                porciones = detalle.get().porciones();
                if (NormalizacionAlimentosServicio.nutricionValida(detalle.get().alimento())) {
                    datos = detalle.get().alimento();
                }
            }
        } catch (ProveedorNoDisponibleException e) {
            LOG.info("No se ha podido pedir el detalle de USDA: se importa sin medidas caseras");
        }
        try {
            return normalizacion.desdeUsda(datos, porciones);
        } catch (ReglaNegocioException e) {
            return null;
        }
    }

    /** Guarda el emparejamiento; si otra petición lo ha guardado a la vez (UNIQUE), se queda con el suyo. */
    private void guardar(String clave, Alimento alimento, BigDecimal confianza) {
        try {
            escritura.executeWithoutResult(estado -> {
                MapeoIngrediente mapeo = new MapeoIngrediente();
                mapeo.setNombreNormalizado(clave);
                mapeo.setAlimento(alimento == null ? null : alimentoRepositorio.getReferenceById(alimento.getId()));
                mapeo.setMetodo(MetodoMapeo.AUTOMATICO);
                mapeo.setConfianza(confianza);
                mapeo.setRevisado(false);
                mapeoRepositorio.saveAndFlush(mapeo);
            });
        } catch (DataIntegrityViolationException e) {
            LOG.debug("El emparejamiento ya lo había guardado otra petición");
        }
    }
}
