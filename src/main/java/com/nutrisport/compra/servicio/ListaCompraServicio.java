package com.nutrisport.compra.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.compra.dto.ActualizarListaRequest;
import com.nutrisport.compra.dto.ActualizarProductoRequest;
import com.nutrisport.compra.dto.AnadirRecetaListaRequest;
import com.nutrisport.compra.dto.GenerarListaRequest;
import com.nutrisport.compra.dto.ListaCompraResponse;
import com.nutrisport.compra.dto.ListaCompraResumenResponse;
import com.nutrisport.compra.dto.ProductoCompraRequest;
import com.nutrisport.compra.dto.ProductoCompraResponse;
import com.nutrisport.compra.dto.SeccionCompraResponse;
import com.nutrisport.compra.entidad.ListaCompra;
import com.nutrisport.compra.entidad.ProductoCompra;
import com.nutrisport.compra.entidad.UnidadCompra;
import com.nutrisport.compra.repositorio.ListaCompraRepositorio;
import com.nutrisport.compra.repositorio.ProductoCompraRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listas de la compra del usuario autenticado (§3.E y §7.6, Ampliación): generarlas desde un menú, consultarlas
 * agrupadas por sección, renombrarlas o completarlas, y añadir, editar, marcar y quitar productos. Las listas, los
 * productos, los menús y las recetas privadas de otro usuario responden 404.
 */
@Service
@Transactional
public class ListaCompraServicio {

    static final int TAMANO_MAXIMO_PAGINA = 50;
    static final int LONGITUD_MAXIMA_NOMBRE = 100;
    static final int LONGITUD_MAXIMA_PRODUCTO = 120;

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String LISTA_NO_ENCONTRADA = "La lista de la compra no existe";

    private final ListaCompraRepositorio listaRepositorio;
    private final ProductoCompraRepositorio productoRepositorio;
    private final MenuSemanalRepositorio menuRepositorio;
    private final RecetaRepositorio recetaRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final GeneradorListaCompra generador;
    private final SeguridadUtils seguridadUtils;

    public ListaCompraServicio(ListaCompraRepositorio listaRepositorio, ProductoCompraRepositorio productoRepositorio,
            MenuSemanalRepositorio menuRepositorio, RecetaRepositorio recetaRepositorio,
            CategoriaRepositorio categoriaRepositorio, UsuarioRepositorio usuarioRepositorio,
            GeneradorListaCompra generador, SeguridadUtils seguridadUtils) {
        this.listaRepositorio = listaRepositorio;
        this.productoRepositorio = productoRepositorio;
        this.menuRepositorio = menuRepositorio;
        this.recetaRepositorio = recetaRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.generador = generador;
        this.seguridadUtils = seguridadUtils;
    }

    // ---------------------------------------------------------------------------------------------
    // Listas
    // ---------------------------------------------------------------------------------------------

    /**
     * Genera una lista ACTIVA con los ingredientes de las comidas no consumidas del menú en el rango
     * ({@link GeneradorListaCompra}). Sin fechas, el menú completo. Si no queda nada que comprar, la lista se crea
     * vacía (se le pueden añadir productos a mano).
     *
     * @param peticion opcional
     * @throws RecursoNoEncontradoException 404 si el menú no existe o es de otro usuario
     * @throws PeticionInvalidaException    400 si desde o hasta quedan fuera del menú, o hasta es anterior a desde
     */
    public ListaCompraResponse generarDesdeMenu(UUID menuId, GenerarListaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        MenuSemanal menu = menuRepositorio.findByIdAndUsuarioId(menuId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El menú no existe"));
        LocalDate desde = peticion != null && peticion.desde() != null ? peticion.desde() : menu.getFechaInicio();
        LocalDate hasta = peticion != null && peticion.hasta() != null ? peticion.hasta() : menu.getFechaFin();
        validarRango(menu, desde, hasta);

        ListaCompra lista = new ListaCompra();
        lista.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        lista.setMenu(menu);
        lista.setNombre(recortar("Compra: " + menu.getNombre(), LONGITUD_MAXIMA_NOMBRE));
        lista.setFechaDesde(desde);
        lista.setFechaHasta(hasta);
        generador.generar(menu, desde, hasta).forEach(lista::anadirProducto);
        return respuesta(listaRepositorio.saveAndFlush(lista));
    }

    /** Listas del usuario, la más reciente primero, con su progreso; como mucho 50 por página. */
    @Transactional(readOnly = true)
    public PaginaResponse<ListaCompraResumenResponse> listar(Pageable paginacion) {
        if (paginacion.getPageSize() > TAMANO_MAXIMO_PAGINA) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo("size",
                    "El tamaño de página no puede superar " + TAMANO_MAXIMO_PAGINA)));
        }
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize());
        return PaginaResponse.de(listaRepositorio.buscar(seguridadUtils.usuarioActualId(), pagina),
                lista -> new ListaCompraResumenResponse(lista.getId(), lista.getNombre(), lista.getEstado(),
                        lista.getMenu() == null ? null : lista.getMenu().getId(), lista.getFechaDesde(),
                        lista.getFechaHasta(), comprados(lista), lista.getProductos().size(), lista.getCreadoEn()));
    }

    @Transactional(readOnly = true)
    public ListaCompraResponse obtener(UUID id) {
        return respuesta(propia(id));
    }

    /** Renombra o cambia de estado (ACTIVA ↔ COMPLETADA); los campos nulos no cambian. */
    public ListaCompraResponse actualizar(UUID id, ActualizarListaRequest peticion) {
        ListaCompra lista = propia(id);
        if (peticion.nombre() != null) {
            lista.setNombre(peticion.nombre().strip());
        }
        if (peticion.estado() != null) {
            lista.setEstado(peticion.estado());
        }
        return respuesta(lista);
    }

    /** Borra la lista con sus productos. */
    public void eliminar(UUID id) {
        listaRepositorio.delete(propia(id));
    }

    // ---------------------------------------------------------------------------------------------
    // Productos
    // ---------------------------------------------------------------------------------------------

    /**
     * Añade un producto libre (manual = true). Sin sección va a "Otros"; una sección que coincide sin distinguir
     * mayúsculas con la de una categoría se escribe como la de la categoría.
     */
    public ListaCompraResponse anadirProducto(UUID id, ProductoCompraRequest peticion) {
        ListaCompra lista = propia(id);
        ProductoCompra producto = new ProductoCompra();
        producto.setNombreLibre(peticion.nombre().strip());
        producto.setCantidad(peticion.cantidad());
        producto.setUnidad(peticion.unidad());
        producto.setSeccion(seccionCanonica(peticion.seccion()));
        producto.setManual(true);
        lista.anadirProducto(producto);
        return respuesta(listaRepositorio.saveAndFlush(lista));
    }

    /**
     * Añade los ingredientes de una receta visible para el usuario, escalados a {@code raciones / raciones de la
     * receta} (los productos nuevos, con manual = true):
     * <ul>
     * <li>Los emparejados con gramos se suman al producto pendiente (no comprado) del mismo alimento en G, KG, ML o L
     * y se vuelve a normalizar la unidad (el producto conserva su {@code manual} y su desglose); si no lo hay, se crea
     * uno nuevo.</li>
     * <li>Los demás se añaden como texto libre con su medida original ("pinch of salt (1 pinch)"), en 1 UD y en la
     * sección de su alimento si lo tienen; uno igual ya pendiente en la lista no se repite.</li>
     * </ul>
     *
     * @throws RecursoNoEncontradoException 404 si la lista o la receta no existen (o la receta es privada de otro)
     */
    public ListaCompraResponse anadirDesdeReceta(UUID id, AnadirRecetaListaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        ListaCompra lista = propia(id, usuarioId);
        Receta receta = recetaRepositorio.findWithIngredientesById(peticion.recetaId())
                .filter(encontrada -> encontrada.esVisiblePara(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe"));
        BigDecimal factor = peticion.raciones()
                .divide(BigDecimal.valueOf(receta.getRaciones()), 6, RoundingMode.HALF_UP);

        for (RecetaIngrediente ingrediente : receta.getIngredientes()) {
            Alimento alimento = ingrediente.getAlimento();
            BigDecimal gramos = ingrediente.getCantidadGramos();
            if (alimento != null && gramos != null && gramos.signum() > 0) {
                sumarAlimento(lista, alimento, gramos.multiply(factor));
            } else {
                anadirTextoLibre(lista, ingrediente);
            }
        }
        return respuesta(listaRepositorio.saveAndFlush(lista));
    }

    /** Cambia la cantidad, la unidad o si está comprado; los campos nulos no cambian. */
    public ListaCompraResponse actualizarProducto(UUID id, UUID productoId, ActualizarProductoRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        ProductoCompra producto = productoPropio(id, productoId, usuarioId);
        if (peticion.cantidad() != null) {
            producto.setCantidad(peticion.cantidad());
        }
        if (peticion.unidad() != null) {
            producto.setUnidad(peticion.unidad());
        }
        if (peticion.comprado() != null) {
            producto.setComprado(peticion.comprado());
        }
        return respuesta(propia(id, usuarioId));
    }

    public void eliminarProducto(UUID id, UUID productoId) {
        ProductoCompra producto = productoPropio(id, productoId, seguridadUtils.usuarioActualId());
        producto.getLista().quitarProducto(producto);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private ListaCompra propia(UUID id) {
        return propia(id, seguridadUtils.usuarioActualId());
    }

    private ListaCompra propia(UUID id, UUID usuarioId) {
        return listaRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(LISTA_NO_ENCONTRADA));
    }

    private ProductoCompra productoPropio(UUID listaId, UUID productoId, UUID usuarioId) {
        return productoRepositorio.buscarDelUsuario(productoId, listaId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto no existe"));
    }

    private static void validarRango(MenuSemanal menu, LocalDate desde, LocalDate hasta) {
        String dentro = "Debe estar dentro del menú, entre " + menu.getFechaInicio() + " y " + menu.getFechaFin();
        List<ErrorCampo> errores = new ArrayList<>();
        if (!menu.contiene(desde)) {
            errores.add(new ErrorCampo("desde", dentro));
        }
        if (!menu.contiene(hasta)) {
            errores.add(new ErrorCampo("hasta", dentro));
        } else if (hasta.isBefore(desde)) {
            errores.add(new ErrorCampo("hasta", "Debe ser igual o posterior a desde"));
        }
        if (!errores.isEmpty()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, errores);
        }
    }

    /** Suma los gramos al producto pendiente del alimento (si su unidad es convertible) o crea uno nuevo. */
    private static void sumarAlimento(ListaCompra lista, Alimento alimento, BigDecimal gramos) {
        boolean liquido = alimento.getUnidadBase() == UnidadBase.ML;
        Optional<ProductoCompra> pendiente = lista.getProductos().stream()
                .filter(producto -> !producto.isComprado() && producto.getAlimento() != null
                        && producto.getAlimento().getId().equals(alimento.getId())
                        && GeneradorListaCompra.enGramos(producto.getCantidad(), producto.getUnidad()) != null)
                .findFirst();
        if (pendiente.isPresent()) {
            ProductoCompra producto = pendiente.get();
            BigDecimal actuales = GeneradorListaCompra.enGramos(producto.getCantidad(), producto.getUnidad());
            GeneradorListaCompra.fijarCantidad(producto, actuales.add(gramos), liquido);
            return;
        }
        ProductoCompra nuevo = new ProductoCompra();
        nuevo.setAlimento(alimento);
        nuevo.setNombreLibre(recortar(alimento.getNombre(), LONGITUD_MAXIMA_PRODUCTO));
        GeneradorListaCompra.fijarCantidad(nuevo, gramos, liquido);
        if (nuevo.getCantidad().signum() <= 0) {
            return;
        }
        nuevo.setSeccion(GeneradorListaCompra.seccion(alimento));
        nuevo.setManual(true);
        lista.anadirProducto(nuevo);
    }

    /** "nombre (medida)" en 1 UD; no se repite si ya hay uno igual pendiente. */
    private static void anadirTextoLibre(ListaCompra lista, RecetaIngrediente ingrediente) {
        Alimento alimento = ingrediente.getAlimento();
        String nombre = alimento != null ? alimento.getNombre() : ingrediente.getNombreOriginal();
        if (nombre == null || nombre.isBlank()) {
            return;
        }
        String medida = ingrediente.getMedidaOriginal();
        String texto = recortar(medida == null || medida.isBlank() ? nombre.strip()
                : nombre.strip() + " (" + medida.strip() + ")", LONGITUD_MAXIMA_PRODUCTO);
        boolean repetido = lista.getProductos().stream()
                .anyMatch(producto -> !producto.isComprado() && producto.getAlimento() == null
                        && texto.equalsIgnoreCase(producto.getNombreLibre()));
        if (repetido) {
            return;
        }
        ProductoCompra producto = new ProductoCompra();
        producto.setNombreLibre(texto);
        producto.setCantidad(BigDecimal.ONE.setScale(2));
        producto.setUnidad(UnidadCompra.UD);
        producto.setSeccion(alimento != null ? GeneradorListaCompra.seccion(alimento)
                : GeneradorListaCompra.SECCION_POR_DEFECTO);
        producto.setManual(true);
        lista.anadirProducto(producto);
    }

    /** Secciones de las categorías de alimentos, sin repetir, en el orden de las categorías. */
    private List<String> seccionesOrdenadas() {
        return categoriaRepositorio.findByTipoOrderByOrden(TipoCategoria.ALIMENTO).stream()
                .map(Categoria::getSeccionCompra)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String seccionCanonica(String seccion) {
        if (seccion == null || seccion.isBlank()) {
            return GeneradorListaCompra.SECCION_POR_DEFECTO;
        }
        String limpia = seccion.strip();
        return seccionesOrdenadas().stream().filter(limpia::equalsIgnoreCase).findFirst().orElse(limpia);
    }

    /**
     * Detalle agrupado por sección: las secciones en el orden de las categorías (las demás al final, por orden
     * alfabético) y, dentro de cada una, primero los pendientes y después los comprados, cada grupo en su orden.
     */
    private ListaCompraResponse respuesta(ListaCompra lista) {
        List<String> orden = seccionesOrdenadas();
        Comparator<String> porSeccion = Comparator
                .comparingInt((String seccion) -> orden.contains(seccion) ? orden.indexOf(seccion) : orden.size())
                .thenComparing(Comparator.naturalOrder());
        Map<String, List<ProductoCompraResponse>> agrupados = new LinkedHashMap<>();
        lista.getProductos().stream()
                .sorted(Comparator.comparing(ProductoCompra::getSeccion, porSeccion)
                        .thenComparing(ProductoCompra::isComprado)
                        .thenComparingInt(ProductoCompra::getOrden))
                .forEach(producto -> agrupados.computeIfAbsent(producto.getSeccion(), s -> new ArrayList<>())
                        .add(new ProductoCompraResponse(producto.getId(), producto.getNombre(),
                                producto.getCantidad(), producto.getUnidad(), producto.getSeccion(),
                                producto.isComprado(), producto.isManual(), List.copyOf(producto.getDesglose()))));
        List<SeccionCompraResponse> secciones = agrupados.entrySet().stream()
                .map(seccion -> new SeccionCompraResponse(seccion.getKey(), List.copyOf(seccion.getValue())))
                .toList();
        return new ListaCompraResponse(lista.getId(), lista.getNombre(), lista.getEstado(),
                lista.getMenu() == null ? null : lista.getMenu().getId(), lista.getFechaDesde(), lista.getFechaHasta(),
                comprados(lista), lista.getProductos().size(), secciones);
    }

    private static int comprados(ListaCompra lista) {
        return (int) lista.getProductos().stream().filter(ProductoCompra::isComprado).count();
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
