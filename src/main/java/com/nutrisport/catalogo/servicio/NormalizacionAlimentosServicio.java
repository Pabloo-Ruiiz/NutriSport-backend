package com.nutrisport.catalogo.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoFuenteRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.integracion.openfoodfacts.ProductoExternoDto;
import com.nutrisport.integracion.openfoodfacts.TablaAlergenosOff;
import com.nutrisport.integracion.openfoodfacts.TablaCategoriasOff;
import com.nutrisport.perfil.entidad.Alergeno;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convierte los productos de los proveedores externos en alimentos del catálogo, con su procedencia
 * ({@code AlimentoFuente}), sin duplicarlos (docs/API_INTEGRATIONS.md §3.4 y §6.2).
 * <p>
 * Los datos del proveedor no se dan por buenos: los valores fuera de rango se descartan (los secundarios
 * quedan nulos, nunca 0), los alérgenos quedan sin revisar y el producto no se marca como validado.
 */
@Service
@Transactional
public class NormalizacionAlimentosServicio {

    static final String LICENCIA_OFF = "ODbL";
    static final String URL_FICHA_OFF = "https://world.openfoodfacts.org/product/";
    /** Porción casera que se crea con la ración del envase (§3.4). */
    static final String PORCION_ENVASE = "ración del envase";

    private static final int LONGITUD_NOMBRE = 150;
    private static final int LONGITUD_MARCA = 80;
    private static final int LONGITUD_RACION = 60;
    private static final int LONGITUD_URL = 300;
    /** Límite prudente para el texto de ingredientes (columna text). */
    private static final int LONGITUD_INGREDIENTES = 10_000;
    private static final BigDecimal MAXIMO_KCAL = new BigDecimal("900");
    private static final BigDecimal MAXIMO_GRAMOS_EN_100 = new BigDecimal("100");
    private static final BigDecimal MAXIMO_RACION = new BigDecimal("5000");
    private static final Pattern CODIGO = Pattern.compile("\\d{4,14}");

    /** Mensaje del 422 NUTRICION_INCOMPLETA (§3.4). */
    public static final String MENSAJE_NUTRICION_INCOMPLETA =
            "Información nutricional incompleta: este producto no se puede añadir. Puedes crear un alimento propio "
                    + "con sus datos";

    private final AlimentoRepositorio alimentoRepositorio;
    private final AlimentoFuenteRepositorio fuenteRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final Clock reloj;

    public NormalizacionAlimentosServicio(AlimentoRepositorio alimentoRepositorio,
            AlimentoFuenteRepositorio fuenteRepositorio, CategoriaRepositorio categoriaRepositorio, Clock reloj) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.fuenteRepositorio = fuenteRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.reloj = reloj;
    }

    /**
     * Crea o actualiza el alimento de un producto de Open Food Facts y su procedencia (licencia ODbL, enlace a
     * la ficha, fecha de sincronización). Lo busca primero por su procedencia y, si no, por código de barras:
     * <ul>
     * <li>No existe: se crea un alimento PUBLICO, fuente OPEN_FOOD_FACTS, preparación ENVASADO, en la
     * categoría equivalente o en "Otros", con los alérgenos sin revisar y sin aptitudes para dietas (el
     * proveedor no las informa).</li>
     * <li>Existe y viene de Open Food Facts: se actualizan sus datos de producto. La categoría no cambia (la
     * puede haber corregido un administrador) y la revisión de alérgenos se conserva solo si los alérgenos y
     * la lactosa no han cambiado.</li>
     * <li>Existe y es un alimento público de otra fuente (BEDCA, administración...): no se tocan sus datos,
     * que tienen prioridad; solo se registra que también está en Open Food Facts.</li>
     * </ul>
     *
     * @throws ReglaNegocioException 422 NUTRICION_INCOMPLETA si faltan la energía o algún macronutriente, o
     *                               tienen valores imposibles
     * @throws ConflictoException    409 CODIGO_BARRAS_EN_USO si el código ya lo tiene un alimento privado
     */
    public Alimento desdeOpenFoodFacts(ProductoExternoDto producto) {
        String codigo = validarCodigo(producto.codigo());
        Nutricion nutricion = Nutricion.de(producto);

        AlimentoFuente fuente = fuenteRepositorio
                .findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, codigo)
                .orElse(null);
        Alimento alimento = fuente != null
                ? fuente.getAlimento()
                : alimentoRepositorio.findByCodigoBarras(codigo).orElse(null);

        if (alimento == null) {
            alimento = nuevoAlimento(producto, codigo);
            aplicarDatos(producto, nutricion, alimento, true);
            alimento = alimentoRepositorio.save(alimento);
        } else if (alimento.getFuente() == FuenteDato.OPEN_FOOD_FACTS) {
            aplicarDatos(producto, nutricion, alimento, false);
        } else if (alimento.getVisibilidad() != Visibilidad.PUBLICO) {
            throw new ConflictoException("CODIGO_BARRAS_EN_USO",
                    "Ya tienes un alimento propio con este código de barras");
        }

        if (fuente == null) {
            fuente = new AlimentoFuente();
            fuente.setProveedor(FuenteDato.OPEN_FOOD_FACTS);
            fuente.setReferenciaExterna(codigo);
        }
        fuente.setAlimento(alimento);
        fuente.setUrl(URL_FICHA_OFF + codigo);
        fuente.setLicencia(LICENCIA_OFF);
        fuente.setSincronizadoEn(OffsetDateTime.now(reloj));
        fuenteRepositorio.save(fuente);
        return alimento;
    }

    /**
     * Si el producto tiene la energía y los tres macronutrientes con valores posibles, es decir, si
     * {@link #desdeOpenFoodFacts} lo aceptaría. Es estático (sin transacción) para comprobarlo antes de llamar
     * a la normalización: una excepción dentro de un método transaccional marcaría como fallida la transacción
     * del llamador aunque este la capturase.
     */
    public static boolean nutricionValida(ProductoExternoDto producto) {
        return Nutricion.leer(producto) != null;
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Campos que solo se fijan al crear el alimento. */
    private Alimento nuevoAlimento(ProductoExternoDto producto, String codigo) {
        Alimento alimento = new Alimento();
        alimento.setCodigoBarras(codigo);
        alimento.setCategoria(categoria(producto.categoriasTags()));
        alimento.setPreparacion(Preparacion.ENVASADO);
        alimento.setFuente(FuenteDato.OPEN_FOOD_FACTS);
        alimento.setReferenciaExterna(codigo);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setPropietario(null);
        // Datos de terceros sin revisar por nosotros
        alimento.setValidado(false);
        // Open Food Facts no lo informa en los campos que pedimos: no se presupone ninguna dieta
        alimento.setAptoVegano(false);
        alimento.setAptoVegetariano(false);
        alimento.setAptoPescetariano(false);
        return alimento;
    }

    /** Datos de producto que se actualizan en cada sincronización. */
    private static void aplicarDatos(ProductoExternoDto producto, Nutricion nutricion, Alimento alimento,
            boolean nuevo) {
        String nombre = producto.nombre() != null ? producto.nombre() : "Producto " + alimento.getCodigoBarras();
        alimento.setNombre(recortar(nombre, LONGITUD_NOMBRE));
        alimento.setMarca(recortar(producto.marca(), LONGITUD_MARCA));
        alimento.setUnidadBase(producto.unidadBase());
        alimento.setKcal100(nutricion.kcal());
        alimento.setProteinas100(nutricion.proteinas());
        alimento.setCarbohidratos100(nutricion.carbohidratos());
        alimento.setGrasas100(nutricion.grasas());
        alimento.setAzucares100(secundario(producto.azucares100()));
        alimento.setSaturadas100(secundario(producto.saturadas100()));
        alimento.setFibra100(secundario(producto.fibra100()));
        alimento.setSal100(secundario(producto.sal100()));
        alimento.setIngredientesTexto(recortar(producto.ingredientesTexto(), LONGITUD_INGREDIENTES));
        alimento.setImagenUrl(urlSegura(producto.imagenUrl()));

        BigDecimal racion = racion(producto.racionGramos());
        alimento.setRacionGramos(racion);
        alimento.setRacionDescripcion(racion == null ? null : recortar(producto.racionDescripcion(), LONGITUD_RACION));
        List<PorcionCasera> porciones = new ArrayList<>(alimento.getPorciones());
        porciones.removeIf(porcion -> PORCION_ENVASE.equals(porcion.nombre()));
        if (racion != null) {
            porciones.add(new PorcionCasera(PORCION_ENVASE, racion));
        }
        alimento.setPorciones(porciones);

        Set<Alergeno> contiene = TablaAlergenosOff.traducir(producto.alergenosTags());
        // Las trazas de un alérgeno que ya contiene no se repiten
        Set<Alergeno> trazas = TablaAlergenosOff.traducir(producto.trazasTags());
        trazas.removeAll(contiene);
        List<String> nuevosContiene = nombres(contiene);
        List<String> nuevasTrazas = nombres(trazas);
        // Sin dato de lactosa en el proveedor: si contiene leche se asume que tiene lactosa (lo prudente)
        boolean lactosa = contiene.contains(Alergeno.LACTEOS);

        boolean sinCambios = !nuevo
                && nuevosContiene.equals(alimento.getAlergenosContiene())
                && nuevasTrazas.equals(alimento.getAlergenosTrazas())
                && lactosa == alimento.isContieneLactosa();
        alimento.setAlergenosRevisados(sinCambios && alimento.isAlergenosRevisados());
        alimento.setAlergenosContiene(nuevosContiene);
        alimento.setAlergenosTrazas(nuevasTrazas);
        alimento.setContieneLactosa(lactosa);
    }

    private Categoria categoria(List<String> etiquetas) {
        return TablaCategoriasOff.categoriaPara(etiquetas)
                .flatMap(categoriaRepositorio::findById)
                .or(() -> categoriaRepositorio.findById(TablaCategoriasOff.OTROS))
                .orElseThrow(() -> new IllegalStateException("Falta la categoría Otros (R__categorias.sql)"));
    }

    private static String validarCodigo(String codigo) {
        if (codigo == null || !CODIGO.matcher(codigo.strip()).matches()) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos",
                    List.of(new ErrorCampo("codigo", "El código de barras debe tener entre 4 y 14 dígitos")));
        }
        return codigo.strip();
    }

    /** Nutriente secundario por 100: entre 0 y 100; si no, nulo (dato no disponible, nunca 0). */
    private static BigDecimal secundario(BigDecimal valor) {
        return enRango(valor, MAXIMO_GRAMOS_EN_100);
    }

    private static BigDecimal enRango(BigDecimal valor, BigDecimal maximo) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(maximo) > 0) {
            return null;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal racion(BigDecimal gramos) {
        if (gramos == null || gramos.signum() <= 0 || gramos.compareTo(MAXIMO_RACION) > 0) {
            return null;
        }
        BigDecimal redondeada = gramos.setScale(1, RoundingMode.HALF_UP);
        return redondeada.signum() > 0 ? redondeada : null;
    }

    /** Solo enlaces https y que caben en la columna; si no, sin imagen. */
    private static String urlSegura(String url) {
        return url != null && url.startsWith("https://") && url.length() <= LONGITUD_URL ? url : null;
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo).strip();
    }

    private static List<String> nombres(Set<Alergeno> alergenos) {
        return alergenos.stream().map(Enum::name).toList();
    }

    /** Energía y macronutrientes obligatorios, ya comprobados. */
    private record Nutricion(BigDecimal kcal, BigDecimal proteinas, BigDecimal carbohidratos, BigDecimal grasas) {

        static Nutricion de(ProductoExternoDto producto) {
            Nutricion nutricion = leer(producto);
            if (nutricion == null) {
                throw new ReglaNegocioException("NUTRICION_INCOMPLETA", MENSAJE_NUTRICION_INCOMPLETA);
            }
            return nutricion;
        }

        /** Nulo si falta la energía o algún macronutriente, o tienen valores imposibles. */
        static Nutricion leer(ProductoExternoDto producto) {
            BigDecimal kcal = enRango(producto.kcal100(), MAXIMO_KCAL);
            BigDecimal proteinas = enRango(producto.proteinas100(), MAXIMO_GRAMOS_EN_100);
            BigDecimal carbohidratos = enRango(producto.carbohidratos100(), MAXIMO_GRAMOS_EN_100);
            BigDecimal grasas = enRango(producto.grasas100(), MAXIMO_GRAMOS_EN_100);
            if (kcal == null || proteinas == null || carbohidratos == null || grasas == null) {
                return null;
            }
            return new Nutricion(kcal, proteinas, carbohidratos, grasas);
        }
    }
}
