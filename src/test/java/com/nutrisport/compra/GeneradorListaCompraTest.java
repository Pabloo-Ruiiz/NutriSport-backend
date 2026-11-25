package com.nutrisport.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.compra.entidad.EntradaDesglose;
import com.nutrisport.compra.entidad.ProductoCompra;
import com.nutrisport.compra.entidad.UnidadCompra;
import com.nutrisport.compra.servicio.GeneradorListaCompra;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Generación de los productos de la compra de un menú (§3.E), sin Spring ni base de datos. */
class GeneradorListaCompraTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 30);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);

    private final GeneradorListaCompra generador = new GeneradorListaCompra();

    private final Categoria despensa = categoria("Cereales y derivados", "Despensa");
    private final Categoria carnes = categoria("Carnes", "Carnes");
    private final Alimento arroz = alimento("Arroz", despensa, UnidadBase.G);
    private final Alimento pollo = alimento("Pechuga de pollo", carnes, UnidadBase.G);

    @Test
    void sumaElMismoAlimentoDeVariasRecetasConSuDesglosePorDia() {
        Receta a = receta("Arroz blanco", 1, ingrediente(arroz, "200"));
        Receta b = receta("Arroz con verduras", 1, ingrediente(arroz, "150"));
        MenuSemanal menu = menu(comida(LUNES, a, "1"), comida(MIERCOLES, b, "1"));

        List<ProductoCompra> productos = generador.generar(menu, null, null);

        assertThat(productos).hasSize(1);
        ProductoCompra producto = productos.get(0);
        assertThat(producto.getNombre()).isEqualTo("Arroz");
        assertThat(producto.getAlimento()).isSameAs(arroz);
        assertThat(producto.getCantidad()).isEqualByComparingTo("350");
        assertThat(producto.getCantidad().scale()).isEqualTo(2);
        assertThat(producto.getUnidad()).isEqualTo(UnidadCompra.G);
        assertThat(producto.getSeccion()).isEqualTo("Despensa");
        assertThat(producto.getDesglose()).containsExactly(
                new EntradaDesglose(LUNES, new BigDecimal("200.0")),
                new EntradaDesglose(MIERCOLES, new BigDecimal("150.0")));
        // Sin guardar ni marcar
        assertThat(producto.getId()).isNull();
        assertThat(producto.getLista()).isNull();
        assertThat(producto.isComprado()).isFalse();
        assertThat(producto.isManual()).isFalse();
    }

    @Test
    void desdeMilGramosPasaAKilosConDosDecimales() {
        // 600 g para 2 raciones × 4 raciones = 1200 g
        Receta receta = receta("Pollo al horno", 2, ingrediente(pollo, "600"));
        MenuSemanal menu = menu(comida(LUNES, receta, "4"));

        ProductoCompra producto = generador.generar(menu, null, null).get(0);

        assertThat(producto.getCantidad()).isEqualByComparingTo("1.20");
        assertThat(producto.getCantidad().scale()).isEqualTo(2);
        assertThat(producto.getUnidad()).isEqualTo(UnidadCompra.KG);
        assertThat(producto.getSeccion()).isEqualTo("Carnes");
        // El desglose sigue en gramos
        assertThat(producto.getDesglose()).containsExactly(new EntradaDesglose(LUNES, new BigDecimal("1200.0")));
    }

    @Test
    void losLiquidosVanEnMililitrosOLitros() {
        Alimento leche = alimento("Leche", categoria("Lácteos y alternativas", "Lácteos y alternativas"), UnidadBase.ML);
        MenuSemanal menu = menu(comida(LUNES, receta("Batido", 1, ingrediente(leche, "250")), "1"),
                comida(MIERCOLES, receta("Natillas", 1, ingrediente(leche, "800")), "1"));

        ProductoCompra producto = generador.generar(menu, null, null).get(0);

        assertThat(producto.getCantidad()).isEqualByComparingTo("1.05");
        assertThat(producto.getUnidad()).isEqualTo(UnidadCompra.L);
    }

    @Test
    void unaComidaConsumidaNoSuma() {
        Receta a = receta("Arroz blanco", 1, ingrediente(arroz, "200"));
        Receta b = receta("Arroz con verduras", 1, ingrediente(arroz, "150"));
        ComidaPlanificada consumida = comida(LUNES, a, "1");
        consumida.setConsumida(true);
        MenuSemanal menu = menu(consumida, comida(MIERCOLES, b, "1"));

        ProductoCompra producto = generador.generar(menu, null, null).get(0);

        assertThat(producto.getCantidad()).isEqualByComparingTo("150");
        assertThat(producto.getDesglose()).containsExactly(new EntradaDesglose(MIERCOLES, new BigDecimal("150.0")));

        // Si todo está consumido, no hay nada que comprar
        ComidaPlanificada unica = comida(LUNES, a, "1");
        unica.setConsumida(true);
        assertThat(generador.generar(menu(unica), null, null)).isEmpty();
    }

    @Test
    void soloSumaLasComidasDelRango() {
        Receta a = receta("Arroz blanco", 1, ingrediente(arroz, "200"));
        Receta b = receta("Arroz con verduras", 1, ingrediente(arroz, "150"));
        MenuSemanal menu = menu(comida(LUNES, a, "1"), comida(MIERCOLES, b, "1"));

        assertThat(generador.generar(menu, LUNES.plusDays(1), null).get(0).getCantidad()).isEqualByComparingTo("150");
        assertThat(generador.generar(menu, null, LUNES).get(0).getCantidad()).isEqualByComparingTo("200");
        assertThatThrownBy(() -> generador.generar(menu, MIERCOLES, LUNES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoraLosIngredientesSinEmparejarYOrdenaPorNombreConOtrosComoSeccionPorDefecto() {
        Alimento sal = alimento("Sal", null, UnidadBase.G);
        RecetaIngrediente sinEmparejar = new RecetaIngrediente("pepper", "to taste");
        RecetaIngrediente sinGramos = new RecetaIngrediente("Arroz", "1 cup");
        sinGramos.setAlimento(arroz);
        Receta receta = receta("Arroz con pollo", 2, ingrediente(pollo, "300"), ingrediente(sal, "5"),
                sinEmparejar, sinGramos);
        MenuSemanal menu = menu(comida(LUNES, receta, "1"));

        List<ProductoCompra> productos = generador.generar(menu, null, null);

        assertThat(productos).extracting(ProductoCompra::getNombre).containsExactly("Pechuga de pollo", "Sal");
        assertThat(productos.get(0).getCantidad()).isEqualByComparingTo("150");
        assertThat(productos.get(1).getCantidad()).isEqualByComparingTo("2.5");
        assertThat(productos.get(1).getSeccion()).isEqualTo(GeneradorListaCompra.SECCION_POR_DEFECTO);
    }

    // ---------------------------------------------------------------------------------------------

    private static MenuSemanal menu(ComidaPlanificada... comidas) {
        MenuSemanal menu = new MenuSemanal();
        menu.setNombre("Semana");
        menu.setTipo(TipoMenu.SEMANAL);
        menu.setFechaInicio(LUNES);
        menu.setFechaFin(LUNES.plusDays(6));
        for (ComidaPlanificada comida : comidas) {
            menu.anadirComida(comida);
        }
        return menu;
    }

    private static ComidaPlanificada comida(LocalDate fecha, Receta receta, String raciones) {
        return new ComidaPlanificada(fecha, TipoComida.COMIDA, receta, new BigDecimal(raciones));
    }

    private static Receta receta(String nombre, int raciones, RecetaIngrediente... ingredientes) {
        Receta receta = new Receta();
        receta.setNombre(nombre);
        receta.setRaciones((short) raciones);
        for (RecetaIngrediente ingrediente : ingredientes) {
            receta.anadirIngrediente(ingrediente);
        }
        return receta;
    }

    private static RecetaIngrediente ingrediente(Alimento alimento, String gramos) {
        RecetaIngrediente ingrediente = new RecetaIngrediente(alimento.getNombre(), gramos + " g");
        ingrediente.setAlimento(alimento);
        ingrediente.setCantidadGramos(new BigDecimal(gramos));
        ingrediente.setEstadoConversion(EstadoConversion.EXACTA);
        return ingrediente;
    }

    private static Alimento alimento(String nombre, Categoria categoria, UnidadBase unidad) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoria);
        alimento.setUnidadBase(unidad);
        // El generador agrupa por id (como con las entidades guardadas)
        ReflectionTestUtils.setField(alimento, "id", UUID.nameUUIDFromBytes(nombre.getBytes()));
        return alimento;
    }

    private static Categoria categoria(String nombre, String seccion) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setSeccionCompra(seccion);
        return categoria;
    }
}
