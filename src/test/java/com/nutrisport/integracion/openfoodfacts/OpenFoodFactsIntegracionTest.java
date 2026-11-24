package com.nutrisport.integracion.openfoodfacts;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoFuenteRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.catalogo.servicio.NormalizacionAlimentosServicio;
import com.nutrisport.comun.config.CacheConfig;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Open Food Facts con el contexto completo de Spring: caché de búsquedas ({@code @Cacheable}, que solo actúa a
 * través del proxy de Spring) y normalización de productos a alimentos del catálogo contra PostgreSQL. El
 * proveedor es un servidor HTTP local: nunca se llama a la API real.
 */
class OpenFoodFactsIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso SERVIDOR = new ServidorHttpFalso();

    private static final UUID CATEGORIA_BEBIDAS = UUID.fromString("c0000000-0000-0000-0000-000000000011");
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");

    @DynamicPropertySource
    static void proveedorLocal(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.off.base-url", SERVIDOR::url);
        registro.add("app.integracion.off.search-url", SERVIDOR::url);
    }

    private final OpenFoodFactsServicio servicio;
    private final NormalizacionAlimentosServicio normalizacion;
    private final AlimentoRepositorio alimentoRepositorio;
    private final AlimentoFuenteRepositorio fuenteRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final CacheManager cacheManager;
    private final LimitadorProveedor limitador;
    private final EstadoProveedor estado;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    @Autowired
    OpenFoodFactsIntegracionTest(OpenFoodFactsServicio servicio, NormalizacionAlimentosServicio normalizacion,
            AlimentoRepositorio alimentoRepositorio, AlimentoFuenteRepositorio fuenteRepositorio,
            CategoriaRepositorio categoriaRepositorio, CacheManager cacheManager, LimitadorProveedor limitador,
            EstadoProveedor estado, JdbcTemplate jdbc, TransactionTemplate transacciones) {
        this.servicio = servicio;
        this.normalizacion = normalizacion;
        this.alimentoRepositorio = alimentoRepositorio;
        this.fuenteRepositorio = fuenteRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.cacheManager = cacheManager;
        this.limitador = limitador;
        this.estado = estado;
        this.jdbc = jdbc;
        this.transacciones = transacciones;
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        SERVIDOR.reiniciar();
        cacheManager.getCache(CacheConfig.OFF_BUSQUEDA).clear();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Caché de búsquedas
    // ---------------------------------------------------------------------------------------------

    @Test
    void laBusquedaSeGuardaEnCachePorTextoNormalizadoYPagina() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/off-busqueda.json")),
                Respuesta.json(recurso("integracion/off-busqueda.json")));

        List<ProductoExternoDto> primera = servicio.buscarPorTexto("Café con LECHE", 1);
        // Mismo texto sin tildes ni mayúsculas y misma página: sale de la caché
        List<ProductoExternoDto> repetida = servicio.buscarPorTexto("cafe con leche", 1);
        assertThat(SERVIDOR.recibidas()).hasSize(1);
        assertThat(repetida).isEqualTo(primera);

        // Otra página es otra búsqueda
        servicio.buscarPorTexto("cafe con leche", 2);
        assertThat(SERVIDOR.recibidas()).hasSize(2);
    }

    @Test
    void losErroresNoSeGuardanEnCache() {
        SERVIDOR.encolar(Respuesta.estado(429));

        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 1)).isInstanceOf(RuntimeException.class);
        estado.reiniciarTodo();
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/off-busqueda.json")));

        assertThat(servicio.buscarPorTexto("arroz", 1)).hasSize(2);
        assertThat(SERVIDOR.recibidas()).hasSize(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Normalización
    // ---------------------------------------------------------------------------------------------

    @Test
    void creaUnAlimentoPublicoConSuProcedenciaDesdeLaRespuestaReal() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/off-producto.json")));
        ProductoExternoDto producto = servicio.buscarPorCodigo("3017624010701").orElseThrow();

        UUID id = normalizacion.desdeOpenFoodFacts(producto).getId();

        transacciones.executeWithoutResult(estadoTx -> {
            Alimento alimento = alimentoRepositorio.findById(id).orElseThrow();
            assertThat(alimento.getNombre()).isEqualTo("Nutella");
            assertThat(alimento.getMarca()).isEqualTo("Ferrero");
            assertThat(alimento.getCodigoBarras()).isEqualTo("3017624010701");
            assertThat(alimento.getVisibilidad()).isEqualTo(Visibilidad.PUBLICO);
            assertThat(alimento.getFuente()).isEqualTo(FuenteDato.OPEN_FOOD_FACTS);
            assertThat(alimento.getReferenciaExterna()).isEqualTo("3017624010701");
            assertThat(alimento.getPreparacion()).isEqualTo(Preparacion.ENVASADO);
            assertThat(alimento.getCategoria().getId()).isEqualTo(CATEGORIA_OTROS);
            assertThat(alimento.getUnidadBase()).isEqualTo(UnidadBase.G);
            assertThat(alimento.getKcal100()).isEqualByComparingTo("539");
            assertThat(alimento.getSal100()).isEqualByComparingTo("0.11");
            // Sin dato: nulo, nunca 0
            assertThat(alimento.getFibra100()).isNull();
            assertThat(alimento.getRacionGramos()).isNull();
            assertThat(alimento.getPorciones()).isEmpty();
            // en:nuts → frutos de cáscara; las trazas de un alérgeno que ya contiene no se repiten
            assertThat(alimento.getAlergenosContiene()).containsExactly("FRUTOS_DE_CASCARA");
            assertThat(alimento.getAlergenosTrazas()).containsExactly("SOJA", "LACTEOS");
            assertThat(alimento.isAlergenosRevisados()).isFalse();
            // La leche solo aparece en trazas
            assertThat(alimento.isContieneLactosa()).isFalse();
            assertThat(alimento.isValidado()).isFalse();
            assertThat(alimento.isAptoVegano()).isFalse();
            assertThat(alimento.getImagenUrl()).startsWith("https://images.openfoodfacts.org/");
        });

        AlimentoFuente fuente = fuenteRepositorio
                .findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, "3017624010701").orElseThrow();
        assertThat(fuente.getAlimento().getId()).isEqualTo(id);
        assertThat(fuente.getUrl()).isEqualTo("https://world.openfoodfacts.org/product/3017624010701");
        assertThat(fuente.getLicencia()).isEqualTo("ODbL");
        assertThat(fuente.getSincronizadoEn()).isNotNull();
    }

    @Test
    void usaLaCategoriaEquivalenteYCreaLaPorcionConLaRacionDelEnvase() {
        SERVIDOR.encolar(Respuesta.json(recurso("integracion/off-producto-ficticio.json")));
        ProductoExternoDto producto = servicio.buscarPorCodigo("8400000000017").orElseThrow();

        UUID id = normalizacion.desdeOpenFoodFacts(producto).getId();

        transacciones.executeWithoutResult(estadoTx -> {
            Alimento alimento = alimentoRepositorio.findById(id).orElseThrow();
            assertThat(alimento.getCategoria().getId()).isEqualTo(CATEGORIA_BEBIDAS);
            assertThat(alimento.getUnidadBase()).isEqualTo(UnidadBase.ML);
            assertThat(alimento.getRacionGramos()).isEqualByComparingTo("250.0");
            assertThat(alimento.getRacionDescripcion()).isEqualTo("250 ml");
            assertThat(alimento.getPorciones())
                    .containsExactly(new PorcionCasera("ración del envase", new BigDecimal("250.0")));
            assertThat(alimento.getIngredientesTexto()).startsWith("Agua, avena");
            // La etiqueta desconocida se ignora; el gluten de las trazas ya está en "contiene"
            assertThat(alimento.getAlergenosContiene()).containsExactly("GLUTEN");
            assertThat(alimento.getAlergenosTrazas()).containsExactly("SESAMO");
            assertThat(alimento.getFibra100()).isNull();
        });
    }

    @Test
    void volverANormalizarActualizaElMismoAlimentoYConservaLaRevisionSiLosAlergenosNoCambian() {
        ProductoExternoDto producto = producto("3017624010701", List.of("en:nuts"), new BigDecimal("539"));
        UUID id = normalizacion.desdeOpenFoodFacts(producto).getId();
        marcarRevisado(id);

        normalizacion.desdeOpenFoodFacts(producto("3017624010701", List.of("en:nuts"), new BigDecimal("540")));

        assertThat(alimentoRepositorio.count()).isEqualTo(1);
        assertThat(fuenteRepositorio.count()).isEqualTo(1);
        Alimento actualizado = alimentoRepositorio.findById(id).orElseThrow();
        assertThat(actualizado.getKcal100()).isEqualByComparingTo("540");
        assertThat(actualizado.isAlergenosRevisados()).isTrue();

        // Si cambian los alérgenos, la revisión anterior deja de valer
        normalizacion.desdeOpenFoodFacts(producto("3017624010701", List.of("en:nuts", "en:milk"), new BigDecimal("540")));

        Alimento conLeche = alimentoRepositorio.findById(id).orElseThrow();
        // "en:milk" → LACTEOS en "contiene"
        assertThat(conLeche.getAlergenosContiene()).containsExactly("LACTEOS", "FRUTOS_DE_CASCARA");
        assertThat(conLeche.isAlergenosRevisados()).isFalse();
        assertThat(conLeche.isContieneLactosa()).isTrue();
        assertThat(alimentoRepositorio.count()).isEqualTo(1);
        assertThat(fuenteRepositorio.count()).isEqualTo(1);
    }

    @Test
    void unAlimentoPublicoDeOtraFuenteConElMismoCodigoNoSeModificaSoloSeRegistraLaProcedencia() {
        Alimento delCatalogo = alimentoDelCatalogo("3017624010701", Visibilidad.PUBLICO);

        Alimento resultado = normalizacion.desdeOpenFoodFacts(
                producto("3017624010701", List.of("en:nuts"), new BigDecimal("539")));

        assertThat(resultado.getId()).isEqualTo(delCatalogo.getId());
        Alimento sinCambios = alimentoRepositorio.findById(delCatalogo.getId()).orElseThrow();
        assertThat(sinCambios.getNombre()).isEqualTo("Crema de cacao (BEDCA)");
        assertThat(sinCambios.getKcal100()).isEqualByComparingTo("530");
        assertThat(sinCambios.getFuente()).isEqualTo(FuenteDato.BEDCA);
        assertThat(fuenteRepositorio.findByProveedorAndReferenciaExterna(FuenteDato.OPEN_FOOD_FACTS, "3017624010701"))
                .get().extracting(fuente -> fuente.getAlimento().getId()).isEqualTo(delCatalogo.getId());
    }

    @Test
    void unAlimentoPrivadoConElMismoCodigoEsUnConflicto() {
        alimentoDelCatalogo("3017624010701", Visibilidad.PRIVADO);

        assertThatThrownBy(() -> normalizacion.desdeOpenFoodFacts(
                producto("3017624010701", List.of(), new BigDecimal("539"))))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CODIGO_BARRAS_EN_USO"));
        assertThat(fuenteRepositorio.count()).isZero();
    }

    @Test
    void sinEnergiaOConValoresImposiblesEsNutricionIncompletaYNoSeGuardaNada() {
        assertThatThrownBy(() -> normalizacion.desdeOpenFoodFacts(producto("3017624010701", List.of(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("NUTRICION_INCOMPLETA"));
        assertThatThrownBy(() -> normalizacion.desdeOpenFoodFacts(
                producto("3017624010701", List.of(), new BigDecimal("4500"))))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(alimentoRepositorio.count()).isZero();
        assertThat(fuenteRepositorio.count()).isZero();
    }

    @Test
    void losNutrientesSecundariosFueraDeRangoQuedanNulos() {
        ProductoExternoDto conAzucarImposible = new ProductoExternoDto("8400000000024", "Producto de prueba", null,
                null, new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("2"),
                new BigDecimal("150"), new BigDecimal("-1"), null, new BigDecimal("0.5"), UnidadBase.G, null, null,
                null, List.of(), List.of(), List.of(), "http://inseguro.example/imagen.jpg");

        Alimento alimento = normalizacion.desdeOpenFoodFacts(conAzucarImposible);

        assertThat(alimento.getAzucares100()).isNull();
        assertThat(alimento.getSaturadas100()).isNull();
        assertThat(alimento.getSal100()).isEqualByComparingTo("0.5");
        // Solo se enlazan imágenes https
        assertThat(alimento.getImagenUrl()).isNull();
    }

    // ---------------------------------------------------------------------------------------------

    private static ProductoExternoDto producto(String codigo, List<String> alergenos, BigDecimal kcal) {
        return new ProductoExternoDto(codigo, "Nutella", "Ferrero", "400 g", kcal, new BigDecimal("6.3"),
                new BigDecimal("57.5"), new BigDecimal("30.9"), null, null, null, null, UnidadBase.G, null, null,
                null, alergenos, List.of(), List.of(), null);
    }

    private void marcarRevisado(UUID id) {
        jdbc.update("UPDATE alimento SET alergenos_revisados = true WHERE id = ?", id);
    }

    /** Alimento que ya estaba en el catálogo con ese código: público de BEDCA o privado de un usuario. */
    private Alimento alimentoDelCatalogo(String codigo, Visibilidad visibilidad) {
        Alimento alimento = new Alimento();
        alimento.setNombre("Crema de cacao (BEDCA)");
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_OTROS).orElseThrow());
        alimento.setCodigoBarras(codigo);
        alimento.setKcal100(new BigDecimal("530"));
        alimento.setProteinas100(new BigDecimal("6"));
        alimento.setCarbohidratos100(new BigDecimal("57"));
        alimento.setGrasas100(new BigDecimal("31"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setVisibilidad(visibilidad);
        if (visibilidad == Visibilidad.PRIVADO) {
            alimento.setFuente(FuenteDato.USUARIO);
            alimento.setPropietario(crearUsuarioYToken().usuario());
        } else {
            alimento.setFuente(FuenteDato.BEDCA);
        }
        return alimentoRepositorio.saveAndFlush(alimento);
    }
}
