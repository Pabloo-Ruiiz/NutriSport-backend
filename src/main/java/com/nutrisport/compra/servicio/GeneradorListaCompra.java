package com.nutrisport.compra.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.compra.entidad.EntradaDesglose;
import com.nutrisport.compra.entidad.ProductoCompra;
import com.nutrisport.compra.entidad.UnidadCompra;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Productos de la compra de un menú (§3.E): para cada comida planificada <b>no consumida</b> del rango, los gramos de
 * cada ingrediente emparejado con el catálogo escalados a las raciones de la comida
 * ({@code cantidad de la receta × raciones / raciones de la receta}), sumados por alimento.
 * <ul>
 * <li>Cantidad: en G (o ML si la unidad base del alimento es ML); desde 1000 pasa a KG (o L). Dos decimales.</li>
 * <li>Sección: {@code seccionCompra} de la categoría del alimento, u "Otros" si no tiene.</li>
 * <li>Desglose: gramos (o ml) por día, con un decimal y en orden de fecha.</li>
 * </ul>
 * Los ingredientes sin emparejar o sin gramos no se incluyen: no hay alimento con el que sumarlos (en las recetas con
 * cálculo COMPLETO o APROXIMADO suelen ser cantidades despreciables, como "una pizca de sal").
 * <p>
 * No usa la base de datos ni guarda nada: devuelve los productos sin lista, ordenados por nombre. Necesita las recetas,
 * sus ingredientes y las categorías de los alimentos (dentro de una transacción si son perezosos).
 */
@Component
public class GeneradorListaCompra {

    public static final String SECCION_POR_DEFECTO = "Otros";

    private static final BigDecimal MIL = new BigDecimal("1000");
    private static final int DECIMALES_FACTOR = 6;

    /** Gramos de un alimento por día, sin redondear hasta el final. */
    private static final class Acumulado {

        private final Alimento alimento;
        private final Map<LocalDate, BigDecimal> porDia = new TreeMap<>();

        Acumulado(Alimento alimento) {
            this.alimento = alimento;
        }

        void sumar(LocalDate fecha, BigDecimal gramos) {
            porDia.merge(fecha, gramos, BigDecimal::add);
        }

        BigDecimal total() {
            return porDia.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * @param desde primer día (incluido); nulo = el primero del menú
     * @param hasta último día (incluido); nulo = el último del menú
     * @return un producto por alimento, sin guardar y sin lista (comprado = false, manual = false)
     * @throws IllegalArgumentException si {@code hasta} es anterior a {@code desde}
     */
    public List<ProductoCompra> generar(MenuSemanal menu, LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde != null ? desde : menu.getFechaInicio();
        LocalDate fin = hasta != null ? hasta : menu.getFechaFin();
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("El rango termina antes de empezar: " + inicio + " – " + fin);
        }

        Map<UUID, Acumulado> porAlimento = new LinkedHashMap<>();
        for (ComidaPlanificada comida : menu.getComidas()) {
            if (comida.isConsumida() || comida.getFecha().isBefore(inicio) || comida.getFecha().isAfter(fin)) {
                continue;
            }
            Receta receta = comida.getReceta();
            BigDecimal factor = comida.getRaciones()
                    .divide(BigDecimal.valueOf(receta.getRaciones()), DECIMALES_FACTOR, RoundingMode.HALF_UP);
            for (RecetaIngrediente ingrediente : receta.getIngredientes()) {
                Alimento alimento = ingrediente.getAlimento();
                BigDecimal cantidad = ingrediente.getCantidadGramos();
                if (alimento == null || cantidad == null || cantidad.signum() <= 0) {
                    continue;
                }
                porAlimento.computeIfAbsent(alimento.getId(), id -> new Acumulado(alimento))
                        .sumar(comida.getFecha(), cantidad.multiply(factor));
            }
        }

        return porAlimento.values().stream()
                .map(GeneradorListaCompra::producto)
                .filter(producto -> producto.getCantidad().signum() > 0)
                .sorted(Comparator.comparing(ProductoCompra::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static ProductoCompra producto(Acumulado acumulado) {
        Alimento alimento = acumulado.alimento;
        boolean liquido = alimento.getUnidadBase() == UnidadBase.ML;
        BigDecimal total = acumulado.total();

        ProductoCompra producto = new ProductoCompra();
        producto.setAlimento(alimento);
        producto.setNombreLibre(alimento.getNombre());
        fijarCantidad(producto, total, liquido);
        producto.setSeccion(seccion(alimento));
        producto.setDesglose(acumulado.porDia.entrySet().stream()
                .map(dia -> new EntradaDesglose(dia.getKey(), dia.getValue().setScale(1, RoundingMode.HALF_UP)))
                .toList());
        return producto;
    }

    /**
     * Fija la cantidad y la unidad a partir de gramos (o ml si {@code liquido}): desde 1000 en KG (o L), si no en G
     * (o ML), con dos decimales.
     */
    public static void fijarCantidad(ProductoCompra producto, BigDecimal gramos, boolean liquido) {
        if (gramos.compareTo(MIL) >= 0) {
            producto.setCantidad(gramos.divide(MIL, 2, RoundingMode.HALF_UP));
            producto.setUnidad(liquido ? UnidadCompra.L : UnidadCompra.KG);
        } else {
            producto.setCantidad(gramos.setScale(2, RoundingMode.HALF_UP));
            producto.setUnidad(liquido ? UnidadCompra.ML : UnidadCompra.G);
        }
    }

    /** Gramos (o ml) de una cantidad en G, KG, ML o L; nulo en UD (no se puede convertir). */
    public static BigDecimal enGramos(BigDecimal cantidad, UnidadCompra unidad) {
        return switch (unidad) {
            case G, ML -> cantidad;
            case KG, L -> cantidad.multiply(MIL);
            case UD -> null;
        };
    }

    /** {@code seccionCompra} de la categoría del alimento, u "Otros". */
    public static String seccion(Alimento alimento) {
        String seccion = alimento.getCategoria() == null ? null : alimento.getCategoria().getSeccionCompra();
        return seccion == null || seccion.isBlank() ? SECCION_POR_DEFECTO : seccion;
    }
}
