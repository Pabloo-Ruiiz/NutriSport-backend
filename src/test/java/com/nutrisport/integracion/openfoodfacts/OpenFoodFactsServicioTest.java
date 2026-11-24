package com.nutrisport.integracion.openfoodfacts;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Peticion;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.OperacionProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Cliente de Open Food Facts (docs/API_INTEGRATIONS.md §3 y §6.3) contra un servidor HTTP local con las
 * respuestas grabadas de {@code src/test/resources/integracion}. Nunca llama a la API real.
 */
class OpenFoodFactsServicioTest {

    private static final String USER_AGENT = "NutriSport-tests/0.1 (tests@ejemplo.com)";
    private static final int TIMEOUT_LECTURA_MS = 300;

    private final ServidorHttpFalso servidor = new ServidorHttpFalso();
    private final Clock reloj = Clock.systemUTC();
    private final EstadoProveedor estado = new EstadoProveedor(reloj);
    private final LimitadorProveedor limitador = new LimitadorProveedor();
    private final OpenFoodFactsServicio servicio = servicio(USER_AGENT);

    @AfterEach
    void cerrar() {
        servidor.close();
    }

    // ---------------------------------------------------------------------------------------------
    // Producto por código
    // ---------------------------------------------------------------------------------------------

    @Test
    void productoPorCodigoConLosCamposDocumentadosUserAgentYAutenticacionDeStaging() {
        servidor.encolar(Respuesta.json(recurso("integracion/off-producto.json")));

        ProductoExternoDto producto = servicio.buscarPorCodigo("3017624010701").orElseThrow();

        Peticion peticion = servidor.recibidas().get(0);
        assertThat(peticion.metodo()).isEqualTo("GET");
        assertThat(peticion.ruta()).isEqualTo("/api/v2/product/3017624010701.json");
        assertThat(peticion.parametro("fields")).isEqualTo(OpenFoodFactsServicio.CAMPOS);
        assertThat(peticion.cabecera("User-Agent")).isEqualTo(USER_AGENT);
        assertThat(peticion.cabecera("Authorization"))
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("off:off".getBytes()));

        assertThat(producto.codigo()).isEqualTo("3017624010701");
        assertThat(producto.nombre()).isEqualTo("Nutella");
        assertThat(producto.marca()).isEqualTo("Ferrero");
        assertThat(producto.unidadBase()).isEqualTo(UnidadBase.G);
        assertThat(producto.kcal100()).isEqualByComparingTo("539");
        assertThat(producto.proteinas100()).isEqualByComparingTo("6.3");
        assertThat(producto.carbohidratos100()).isEqualByComparingTo("57.5");
        assertThat(producto.grasas100()).isEqualByComparingTo("30.9");
        assertThat(producto.azucares100()).isEqualByComparingTo("56.3");
        assertThat(producto.saturadas100()).isEqualByComparingTo("10.6");
        assertThat(producto.sal100()).isEqualByComparingTo("0.1075");
        // Campos que no venían en la respuesta real: nulos, nunca 0
        assertThat(producto.fibra100()).isNull();
        assertThat(producto.racionGramos()).isNull();
        assertThat(producto.racionDescripcion()).isNull();
        assertThat(producto.alergenosTags()).containsExactly("en:nuts");
        assertThat(producto.trazasTags()).containsExactly("en:milk", "en:nuts", "en:soybeans");
        assertThat(producto.imagenUrl()).startsWith("https://images.openfoodfacts.org/");
        assertThat(producto.nutricionCompleta()).isTrue();
    }

    @Test
    void aceptaValoresComoTextoMililitrosYRacion() {
        servidor.encolar(Respuesta.json(recurso("integracion/off-producto-ficticio.json")));

        ProductoExternoDto producto = servicio.buscarPorCodigo("8400000000017").orElseThrow();

        assertThat(producto.unidadBase()).isEqualTo(UnidadBase.ML);
        assertThat(producto.kcal100()).isEqualByComparingTo("46");
        assertThat(producto.proteinas100()).isEqualByComparingTo("1.0");
        // "traces" no es un número: dato no disponible
        assertThat(producto.fibra100()).isNull();
        assertThat(producto.cantidad()).isEqualTo("1 l");
        assertThat(producto.racionGramos()).isEqualByComparingTo("250");
        assertThat(producto.racionDescripcion()).isEqualTo("250 ml");
        assertThat(producto.ingredientesTexto()).startsWith("Agua, avena");
        assertThat(producto.categoriasTags()).contains("en:beverages");
    }

    @Test
    void unProductoInexistenteRespondeStatusCeroConHttp200YDevuelveVacio() {
        servidor.encolar(Respuesta.json(recurso("integracion/off-no-encontrado.json")));

        assertThat(servicio.buscarPorCodigo("0000000000000")).isEmpty();
    }

    @Test
    void unHttp404TambienEsProductoInexistenteYNoMarcaElProveedorComoCaido() {
        servidor.encolar(Respuesta.estado(404));

        assertThat(servicio.buscarPorCodigo("0000000000000")).isEmpty();
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isTrue();
    }

    @Test
    void unaRespuestaConFormaInesperadaSeTrataComoSinDatos() {
        servidor.encolar(new Respuesta(200, Map.of("Content-Type", "application/json"), "<html>no es JSON",
                Duration.ZERO));

        assertThat(servicio.buscarPorCodigo("3017624010701")).isEmpty();
    }

    @Test
    void unCodigoNoValidoSeRechazaSinLlamarAlProveedor() {
        assertThatThrownBy(() -> servicio.buscarPorCodigo("12a4/../x"))
                .isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscarPorCodigo("123"))
                .isInstanceOf(PeticionInvalidaException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda por texto (Search-a-licious)
    // ---------------------------------------------------------------------------------------------

    @Test
    void busquedaEnSearchALiciousConMarcasComoListaYSinAutenticacion() {
        servidor.encolar(Respuesta.json(recurso("integracion/off-busqueda.json")));

        List<ProductoExternoDto> productos = servicio.buscarPorTexto("  café con leche ", 2);

        Peticion peticion = servidor.recibidas().get(0);
        assertThat(peticion.ruta()).isEqualTo("/search");
        assertThat(peticion.parametro("q")).isEqualTo("café con leche");
        assertThat(peticion.parametro("page")).isEqualTo("2");
        assertThat(peticion.parametro("page_size")).isEqualTo("20");
        assertThat(peticion.parametro("fields")).isEqualTo(OpenFoodFactsServicio.CAMPOS);
        assertThat(peticion.cabecera("User-Agent")).isEqualTo(USER_AGENT);
        // La autenticación de staging es de la API: no se envía a otro dominio
        assertThat(peticion.cabecera("Authorization")).isNull();

        // El resultado sin código se descarta
        assertThat(productos).extracting(ProductoExternoDto::codigo).containsExactly("3017624010701", "8400000000017");
        assertThat(productos.get(0).marca()).isEqualTo("Ferrero");
        assertThat(productos.get(1).marca()).isEqualTo("Marca Ficticia, Otra Marca");
        assertThat(productos.get(1).nutricionCompleta()).isFalse();
    }

    @Test
    void unTextoConLlavesSeEnviaCodificadoComoTexto() {
        servidor.encolar(Respuesta.json("{\"hits\": []}"));

        assertThat(servicio.buscarPorTexto("{q}&page=99", 1)).isEmpty();
        assertThat(servidor.recibidas().get(0).uri().getRawQuery()).contains("q=%7Bq%7D%26page%3D99");
    }

    @Test
    void unaBusquedaDemasiadoCortaOUnaPaginaFueraDeRangoSeRechazan() {
        assertThatThrownBy(() -> servicio.buscarPorTexto(" a ", 1)).isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 0)).isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 51)).isInstanceOf(PeticionInvalidaException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Errores, reintento y caída (§6.3)
    // ---------------------------------------------------------------------------------------------

    @Test
    void unErrorDelServidorSeReintentaUnaVez() {
        servidor.encolar(Respuesta.estado(500), Respuesta.json(recurso("integracion/off-producto.json")));

        Optional<ProductoExternoDto> producto = servicio.buscarPorCodigo("3017624010701");

        assertThat(producto).isPresent();
        assertThat(servidor.recibidas()).hasSize(2);
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isTrue();
    }

    @Test
    void dosErroresSeguidosMarcanElProveedorComoCaido60SegundosYYaNoSeLeLlama() {
        servidor.encolar(Respuesta.estado(502), Respuesta.estado(500));

        assertThatThrownBy(() -> servicio.buscarPorCodigo("3017624010701"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
                    assertThat(e.getSegundosReintento()).isEqualTo(60);
                });
        assertThat(servidor.recibidas()).hasSize(2);
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isFalse();

        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 1))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isBetween(59L, 60L));
        assertThat(servidor.recibidas()).hasSize(2);
    }

    @Test
    void unTimeoutSeReintentaYSiVuelveAFallarElProveedorQuedaCaido() {
        Duration lento = Duration.ofMillis(TIMEOUT_LECTURA_MS * 4L);
        servidor.encolar(Respuesta.json("{}").conRetraso(lento), Respuesta.json("{}").conRetraso(lento));

        assertThatThrownBy(() -> servicio.buscarPorCodigo("3017624010701"))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).hasSize(2);
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isFalse();
    }

    @Test
    void un503NoSeReintentaYRespetaRetryAfter() {
        servidor.encolar(Respuesta.estado(503, Map.of("Retry-After", "120")));

        assertThatThrownBy(() -> servicio.buscarPorCodigo("3017624010701"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(120));
        assertThat(servidor.recibidas()).hasSize(1);
        assertThat(estado.segundosHastaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isBetween(119L, 120L);
    }

    @Test
    void un429SinRetryAfterEspera60Segundos() {
        servidor.encolar(Respuesta.estado(429));

        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 1))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(60));
        assertThat(servidor.recibidas()).hasSize(1);
    }

    @Test
    void retryAfterComoFechaHttp() {
        String dentroDe90Segundos = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(90));
        servidor.encolar(Respuesta.estado(429, Map.of("Retry-After", dentroDe90Segundos)));

        assertThatThrownBy(() -> servicio.buscarPorTexto("arroz", 1))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isBetween(88L, 90L));
    }

    @Test
    void unaRespuesta401DeStagingEsUnErrorDeConfiguracionYNoSeReintenta() {
        servidor.encolar(Respuesta.estado(401));

        assertThatThrownBy(() -> servicio.buscarPorCodigo("3017624010701"))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).hasSize(1);
        assertThat(estado.estaDisponible(ProveedorExterno.OPEN_FOOD_FACTS)).isFalse();
    }

    @Test
    void sinCupoNoSeLlamaAlProveedor() {
        // Productos: 3 de ráfaga
        for (int i = 0; i < 3; i++) {
            servidor.encolar(Respuesta.json(recurso("integracion/off-no-encontrado.json")));
            servicio.buscarPorCodigo("0000000000000");
        }

        assertThatThrownBy(() -> servicio.buscarPorCodigo("0000000000000"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isBetween(1L, 5L));
        assertThat(servidor.recibidas()).hasSize(3);
        // El cupo de búsquedas es otro
        assertThat(limitador.intentar(ProveedorExterno.OPEN_FOOD_FACTS, OperacionProveedor.BUSQUEDA)).isTrue();
    }

    @Test
    void sinUserAgentConfiguradoNoSeLlamaAlProveedor() {
        OpenFoodFactsServicio sinContacto = servicio(null);

        assertThatThrownBy(() -> sinContacto.buscarPorCodigo("3017624010701"))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    private OpenFoodFactsServicio servicio(String userAgent) {
        IntegracionProperties propiedades = new IntegracionProperties(
                new IntegracionProperties.Proveedor(servidor.url(), servidor.url(), userAgent, "off", "off", null,
                        1000, TIMEOUT_LECTURA_MS),
                null, null);
        return new OpenFoodFactsServicio(RestClient.builder(), propiedades, limitador, estado, reloj, Duration.ZERO);
    }
}
