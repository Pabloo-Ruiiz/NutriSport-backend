package com.nutrisport.catalogo;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.MapeoIngrediente;
import com.nutrisport.catalogo.entidad.MetodoMapeo;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoFuenteRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.MapeoIngredienteRepositorio;
import com.nutrisport.catalogo.servicio.MapeoIngredientesServicio;
import com.nutrisport.catalogo.servicio.NormalizacionAlimentosServicio;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.usda.AlimentoUsdaDto;
import com.nutrisport.integracion.usda.PorcionUsdaDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Normalización de alimentos de USDA y emparejamiento de ingredientes (docs/API_INTEGRATIONS.md §6.2 y §7.1) con
 * PostgreSQL real y USDA simulado por un servidor HTTP local.
 */
class MapeoIngredientesIntegracionTest extends IntegracionBase {

    private static final ServidorHttpFalso USDA = new ServidorHttpFalso();

    @DynamicPropertySource
    static void proveedorLocal(DynamicPropertyRegistry registro) {
        registro.add("app.integracion.usda.base-url", () -> USDA.url() + "/fdc/v1");
    }

    @Autowired
    private MapeoIngredientesServicio mapeoServicio;
    @Autowired
    private NormalizacionAlimentosServicio normalizacion;
    @Autowired
    private MapeoIngredienteRepositorio mapeoRepositorio;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private AlimentoFuenteRepositorio fuenteRepositorio;
    @Autowired
    private LimitadorProveedor limitador;
    @Autowired
    private EstadoProveedor estado;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transacciones;

    @BeforeEach
    @AfterEach
    void limpiar() {
        USDA.reiniciar();
        limitador.reiniciarTodo();
        estado.reiniciarTodo();
        jdbc.execute("TRUNCATE mapeo_ingrediente, alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // desdeUsda
    // ---------------------------------------------------------------------------------------------

    @Test
    void desdeUsdaCreaUnAlimentoPublicoConSusMedidasYLaProcedenciaCc0YLuegoLoReutiliza() {
        Alimento creado = normalizacion.desdeUsda(banana(), List.of(
                new PorcionUsdaDto("NLEA serving", BigDecimal.ONE, new BigDecimal("126")),
                new PorcionUsdaDto("cup, sliced", BigDecimal.ONE, new BigDecimal("150")),
                new PorcionUsdaDto("Cup, Sliced", BigDecimal.ONE, new BigDecimal("150")),
                new PorcionUsdaDto("2 slices", new BigDecimal("2"), new BigDecimal("50"))));

        transacciones.executeWithoutResult(tx -> {
            Alimento alimento = alimentoRepositorio.findById(creado.getId()).orElseThrow();
            assertThat(alimento.getNombre()).isEqualTo("Bananas, raw");
            assertThat(alimento.getFuente()).isEqualTo(FuenteDato.USDA);
            assertThat(alimento.getReferenciaExterna()).isEqualTo("173944");
            assertThat(alimento.getVisibilidad()).isEqualTo(Visibilidad.PUBLICO);
            assertThat(alimento.isAlergenosRevisados()).isFalse();
            assertThat(alimento.getCategoria().getNombre()).isEqualTo("Otros");
            assertThat(alimento.getKcal100()).isEqualByComparingTo("89");
            assertThat(alimento.getAzucares100()).isNull();
            // Sin repetir descripciones; "2 slices" = 50 g → 25 g por medida
            assertThat(alimento.getPorciones()).containsExactly(
                    new PorcionCasera("NLEA serving", new BigDecimal("126.0")),
                    new PorcionCasera("cup, sliced", new BigDecimal("150.0")),
                    new PorcionCasera("2 slices", new BigDecimal("25.0")));
        });
        AlimentoFuente fuente = fuenteRepositorio.findByProveedorAndReferenciaExterna(FuenteDato.USDA, "173944")
                .orElseThrow();
        assertThat(fuente.getLicencia()).isEqualTo("CC0");
        assertThat(fuente.getUrl()).isNull();

        Alimento otraVez = normalizacion.desdeUsda(banana(), List.of());
        assertThat(otraVez.getId()).isEqualTo(creado.getId());
        assertThat(alimentoRepositorio.count()).isEqualTo(1);
    }

    @Test
    void desdeUsdaSinEnergiaEsNutricionIncompleta() {
        AlimentoUsdaDto sinEnergia = new AlimentoUsdaDto("170000", "Something, raw", "Foundation", null,
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));

        assertThatThrownBy(() -> normalizacion.desdeUsda(sinEnergia, List.of()))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("NUTRICION_INCOMPLETA"));
        assertThat(alimentoRepositorio.count()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // resolver
    // ---------------------------------------------------------------------------------------------

    @Test
    void unIngredienteNuevoSeBuscaEnUsdaSeImportaYSeGuardaElEmparejamiento() {
        USDA.encolar(Respuesta.json(recurso("integracion/usda-busqueda-banana.json")),
                Respuesta.json(recurso("integracion/usda-detalle-173944.json")));

        Optional<Alimento> alimento = mapeoServicio.resolver("Banana");

        assertThat(alimento).isPresent();
        assertThat(USDA.recibidas()).extracting(ServidorHttpFalso.Peticion::ruta)
                .containsExactly("/fdc/v1/foods/search", "/fdc/v1/food/173944");
        MapeoIngrediente mapeo = mapeoRepositorio.findByNombreNormalizado("banana").orElseThrow();
        assertThat(mapeo.getMetodo()).isEqualTo(MetodoMapeo.AUTOMATICO);
        assertThat(mapeo.isRevisado()).isFalse();
        assertThat(mapeo.getConfianza()).isGreaterThanOrEqualTo(MapeoIngredientesServicio.UMBRAL_CONFIANZA);
        assertThat(mapeo.getAlimento().getId()).isEqualTo(alimento.get().getId());
        // Las medidas caseras vienen del detalle
        assertThat(alimentoRepositorio.findById(alimento.get().getId()).orElseThrow().getPorciones()).hasSize(3);

        // La segunda vez sale del emparejamiento guardado, sin llamar a USDA
        assertThat(mapeoServicio.resolver("  BANANA ")).map(Alimento::getId).contains(alimento.get().getId());
        assertThat(USDA.recibidas()).hasSize(2);
    }

    @Test
    void conPocaConfianzaQuedaPendienteYDevuelveVacioSinVolverAPreguntar() {
        USDA.encolar(Respuesta.json(recurso("integracion/usda-busqueda-sin-coincidencia.json")));

        assertThat(mapeoServicio.resolver("Harissa Spice")).isEmpty();

        MapeoIngrediente pendiente = mapeoRepositorio.findByNombreNormalizado("harissa spice").orElseThrow();
        assertThat(pendiente.getAlimento()).isNull();
        assertThat(pendiente.getConfianza()).isLessThan(MapeoIngredientesServicio.UMBRAL_CONFIANZA);
        assertThat(pendiente.isRevisado()).isFalse();
        // No se importa un alimento de USDA para un emparejamiento dudoso
        assertThat(alimentoRepositorio.count()).isZero();

        assertThat(mapeoServicio.resolver("Harissa Spice")).isEmpty();
        assertThat(USDA.recibidas()).hasSize(1);
    }

    @Test
    void conUsdaCaidoNoSeGuardaNadaParaReintentarDespues() {
        USDA.encolar(Respuesta.estado(500), Respuesta.estado(500));

        assertThat(mapeoServicio.resolver("Banana")).isEmpty();
        assertThat(mapeoRepositorio.count()).isZero();

        estado.reiniciarTodo();
        USDA.encolar(Respuesta.json(recurso("integracion/usda-busqueda-banana.json")),
                Respuesta.json(recurso("integracion/usda-detalle-173944.json")));
        assertThat(mapeoServicio.resolver("Banana")).isPresent();
    }

    @Test
    void unaCoincidenciaCasiExactaEnElCatalogoNoConsultaUsda() {
        Alimento delCatalogo = normalizacion.desdeUsda(banana(), List.of());

        assertThat(mapeoServicio.resolver("Bananas, raw")).map(Alimento::getId).contains(delCatalogo.getId());

        assertThat(USDA.recibidas()).isEmpty();
        assertThat(mapeoRepositorio.findByNombreNormalizado("bananas, raw").orElseThrow().getConfianza())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void unEmparejamientoManualSeUsaAunqueNoTengaConfianza() {
        Alimento delCatalogo = normalizacion.desdeUsda(banana(), List.of());
        transacciones.executeWithoutResult(tx -> {
            MapeoIngrediente manual = new MapeoIngrediente();
            manual.setNombreNormalizado("platano macho");
            manual.setAlimento(alimentoRepositorio.getReferenceById(delCatalogo.getId()));
            manual.setMetodo(MetodoMapeo.MANUAL);
            manual.setRevisado(true);
            mapeoRepositorio.save(manual);
        });

        assertThat(mapeoServicio.resolver("Plátano macho")).map(Alimento::getId).contains(delCatalogo.getId());
        assertThat(USDA.recibidas()).isEmpty();
    }

    private static AlimentoUsdaDto banana() {
        return new AlimentoUsdaDto("173944", "Bananas, raw", "SR Legacy", new BigDecimal("89"), new BigDecimal("1.09"),
                new BigDecimal("22.8"), new BigDecimal("0.33"));
    }
}
