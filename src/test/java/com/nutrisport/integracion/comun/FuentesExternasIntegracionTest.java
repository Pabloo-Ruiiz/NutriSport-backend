package com.nutrisport.integracion.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoFuenteRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.config.CacheConfig;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base común de la integración con APIs externas (Ampliación): migración V11 ({@code alimento_fuente} y
 * datos de producto en {@code alimento}), configuración de los proveedores y cachés.
 */
class FuentesExternasIntegracionTest extends IntegracionBase {

    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");

    private final AlimentoRepositorio alimentoRepositorio;
    private final AlimentoFuenteRepositorio fuenteRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final IntegracionProperties propiedades;
    private final CacheManager cacheManager;
    private final JdbcTemplate jdbc;

    @Autowired
    FuentesExternasIntegracionTest(AlimentoRepositorio alimentoRepositorio, AlimentoFuenteRepositorio fuenteRepositorio,
            CategoriaRepositorio categoriaRepositorio, IntegracionProperties propiedades, CacheManager cacheManager,
            JdbcTemplate jdbc) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.fuenteRepositorio = fuenteRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.propiedades = propiedades;
        this.cacheManager = cacheManager;
        this.jdbc = jdbc;
    }

    @BeforeEach
    @AfterEach
    void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Migración, entidad y repositorio
    // ---------------------------------------------------------------------------------------------

    @Test
    void guardaLosDatosDeProductoYSuProcedencia() {
        Alimento nutella = productoDeOpenFoodFacts();
        guardarFuente(nutella, FuenteDato.OPEN_FOOD_FACTS, "3017624010701");

        AlimentoFuente fuente = fuenteRepositorio
                .findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, "3017624010701")
                .orElseThrow();

        assertThat(fuente.getLicencia()).isEqualTo("ODbL");
        assertThat(fuente.getUrl()).isEqualTo("https://world.openfoodfacts.org/product/3017624010701");
        assertThat(fuente.getSincronizadoEn()).isNotNull();
        // El EntityGraph carga el alimento: se lee fuera de la transacción sin LazyInitializationException
        Alimento alimento = fuente.getAlimento();
        assertThat(alimento.getNombre()).isEqualTo("Nutella");
        assertThat(alimento.getIngredientesTexto()).startsWith("Azúcar");
        assertThat(alimento.getImagenUrl()).startsWith("https://images.openfoodfacts.org/");
        assertThat(alimento.getRacionGramos()).isEqualByComparingTo("15.0");
        assertThat(alimento.getRacionDescripcion()).isEqualTo("1 cucharada (15 g)");

        assertThat(fuenteRepositorio.findByProveedorAndReferenciaExterna(FuenteDato.USDA, "3017624010701")).isEmpty();
    }

    @Test
    void unAlimentoPuedeTenerVariasFuentesPeroCadaReferenciaEsUnicaPorProveedor() {
        Alimento nutella = productoDeOpenFoodFacts();
        guardarFuente(nutella, FuenteDato.OPEN_FOOD_FACTS, "3017624010701");
        guardarFuente(nutella, FuenteDato.BEDCA, "3017624010701");

        assertThat(fuenteRepositorio.count()).isEqualTo(2);
        assertThatThrownBy(() -> guardarFuente(nutella, FuenteDato.OPEN_FOOD_FACTS, "3017624010701"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void soloAdmiteLosProveedoresDeLaRestriccion() {
        Alimento nutella = productoDeOpenFoodFacts();

        assertThatThrownBy(() -> guardarFuente(nutella, FuenteDato.ADMIN, "DEV"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void alBorrarElAlimentoSeBorranSusFuentes() {
        Alimento nutella = productoDeOpenFoodFacts();
        guardarFuente(nutella, FuenteDato.OPEN_FOOD_FACTS, "3017624010701");

        alimentoRepositorio.deleteById(nutella.getId());

        assertThat(fuenteRepositorio.count()).isZero();
    }

    @Test
    void laRacionDebeSerPositiva() {
        Alimento alimento = productoDeOpenFoodFacts();

        assertThatThrownBy(() -> jdbc.update("UPDATE alimento SET racion_gramos = 0 WHERE id = ?", alimento.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Configuración y cachés
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasUrlsDeLosTestsSonFalsasYLosTimeoutsTienenValorPorDefecto() {
        IntegracionProperties.Proveedor off = propiedades.de(ProveedorExterno.OPEN_FOOD_FACTS);

        assertThat(off.baseUrl()).endsWith(".invalid");
        assertThat(off.searchUrl()).endsWith(".invalid");
        assertThat(propiedades.de(ProveedorExterno.THEMEALDB).baseUrl()).endsWith(".invalid");
        assertThat(propiedades.de(ProveedorExterno.USDA).baseUrl()).endsWith(".invalid");
        assertThat(off.usaAutenticacionStaging()).isTrue();
        assertThat(off.timeoutConexionMs()).isEqualTo(2000);
        assertThat(off.timeoutLecturaMs()).isEqualTo(5000);
    }

    @Test
    void elToStringNoMuestraClavesNiContrasenas() {
        String texto = propiedades.toString();

        assertThat(texto).doesNotContain("clave-de-tests").doesNotContain("claveStaging=off");
        assertThat(texto).contains("apiKey=***").contains("claveStaging=***");
    }

    @Test
    void soloExistenLasCachesDeclaradas() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(
                CacheConfig.OFF_BUSQUEDA, CacheConfig.MEALDB_LISTAS, CacheConfig.MEALDB_CATEGORIAS);
        assertThat(cacheManager.getCache("otra")).isNull();
    }

    // ---------------------------------------------------------------------------------------------

    /** Producto de ejemplo de API_INTEGRATIONS.md §3.2, con los datos que aporta Open Food Facts. */
    private Alimento productoDeOpenFoodFacts() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Nutella");
        alimento.setMarca("Ferrero");
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_OTROS).orElseThrow());
        alimento.setCodigoBarras("3017624010701");
        alimento.setPreparacion(Preparacion.ENVASADO);
        alimento.setKcal100(new BigDecimal("539"));
        alimento.setProteinas100(new BigDecimal("6.3"));
        alimento.setCarbohidratos100(new BigDecimal("57.5"));
        alimento.setGrasas100(new BigDecimal("30.9"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setIngredientesTexto("Azúcar, aceite de palma, avellanas 13 %, cacao desgrasado 7,4 %...");
        alimento.setImagenUrl("https://images.openfoodfacts.org/images/products/301/762/401/0701/front_es.jpg");
        alimento.setRacionGramos(new BigDecimal("15.0"));
        alimento.setRacionDescripcion("1 cucharada (15 g)");
        alimento.setFuente(FuenteDato.OPEN_FOOD_FACTS);
        alimento.setReferenciaExterna("3017624010701");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setValidado(true);
        return alimentoRepositorio.saveAndFlush(alimento);
    }

    private void guardarFuente(Alimento alimento, FuenteDato proveedor, String referencia) {
        AlimentoFuente fuente = new AlimentoFuente();
        fuente.setAlimento(alimento);
        fuente.setProveedor(proveedor);
        fuente.setReferenciaExterna(referencia);
        fuente.setUrl("https://world.openfoodfacts.org/product/" + referencia);
        fuente.setLicencia("ODbL");
        fuente.setSincronizadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        fuenteRepositorio.saveAndFlush(fuente);
    }
}
