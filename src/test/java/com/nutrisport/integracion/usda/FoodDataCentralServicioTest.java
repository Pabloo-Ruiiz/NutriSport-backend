package com.nutrisport.integracion.usda;

import static com.nutrisport.integracion.ServidorHttpFalso.recurso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.integracion.ServidorHttpFalso;
import com.nutrisport.integracion.ServidorHttpFalso.Peticion;
import com.nutrisport.integracion.ServidorHttpFalso.Respuesta;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Cliente de USDA FoodData Central (docs/API_INTEGRATIONS.md §5) contra un servidor HTTP local con respuestas
 * de {@code src/test/resources/integracion}. Nunca llama a la API real.
 */
class FoodDataCentralServicioTest {

    private static final String CLAVE = "clave-de-tests";

    private final ServidorHttpFalso servidor = new ServidorHttpFalso();
    private final EstadoProveedor estado = new EstadoProveedor(Clock.systemUTC());
    private final FoodDataCentralServicio servicio = servicio(CLAVE);

    @AfterEach
    void cerrar() {
        servidor.close();
    }

    @Test
    void laBusquedaPideFoundationYSrLegacyConLaClaveYLeeLaEnergiaEnKcal() {
        servidor.encolar(Respuesta.json(recurso("integracion/usda-busqueda-banana.json")));

        List<AlimentoUsdaDto> alimentos = servicio.buscar("banana raw");

        Peticion peticion = servidor.recibidas().get(0);
        assertThat(peticion.ruta()).isEqualTo("/fdc/v1/foods/search");
        assertThat(peticion.parametro("api_key")).isEqualTo(CLAVE);
        assertThat(peticion.parametro("query")).isEqualTo("banana raw");
        assertThat(peticion.parametro("dataType")).isEqualTo("Foundation,SR Legacy");
        assertThat(peticion.parametro("pageSize")).isEqualTo("5");

        AlimentoUsdaDto banana = alimentos.get(0);
        assertThat(banana.fdcId()).isEqualTo("173944");
        assertThat(banana.descripcion()).isEqualTo("Bananas, raw");
        assertThat(banana.tipoDatos()).isEqualTo("SR Legacy");
        // 1008 en KCAL; la energía en kJ (1062) se ignora
        assertThat(banana.kcal100()).isEqualByComparingTo("89");
        assertThat(banana.proteinas100()).isEqualByComparingTo("1.09");
        assertThat(banana.grasas100()).isEqualByComparingTo("0.33");
        assertThat(banana.carbohidratos100()).isEqualByComparingTo("22.8");
        assertThat(banana.nutricionCompleta()).isTrue();
    }

    @Test
    void elDetalleLeeNutrientIdYAmountYLasMedidasCaseras() {
        servidor.encolar(Respuesta.json(recurso("integracion/usda-detalle-173944.json")));

        DetalleUsdaDto detalle = servicio.detalle("173944").orElseThrow();

        Peticion peticion = servidor.recibidas().get(0);
        assertThat(peticion.ruta()).isEqualTo("/fdc/v1/food/173944");
        assertThat(peticion.parametro("format")).isEqualTo("full");
        assertThat(peticion.parametro("api_key")).isEqualTo(CLAVE);
        assertThat(detalle.alimento().kcal100()).isEqualByComparingTo("89");
        assertThat(detalle.alimento().carbohidratos100()).isEqualByComparingTo("22.8");
        // La medida sin gramos se descarta
        assertThat(detalle.porciones()).containsExactly(
                new PorcionUsdaDto("NLEA serving", new BigDecimal("1.0"), new BigDecimal("126.0")),
                new PorcionUsdaDto("cup, sliced", new BigDecimal("1.0"), new BigDecimal("150.0")),
                new PorcionUsdaDto("extra small", new BigDecimal("1.0"), new BigDecimal("81.0")));
    }

    @Test
    void unAlimentoQueNoExisteEsVacio() {
        servidor.encolar(Respuesta.estado(404));

        assertThat(servicio.detalle("1")).isEmpty();
        assertThat(estado.estaDisponible(ProveedorExterno.USDA)).isTrue();
    }

    @Test
    void un429BloqueaUSDAUnaHoraAunqueRetryAfterDigaMenos() {
        servidor.encolar(Respuesta.estado(429, Map.of("Retry-After", "30")));

        assertThatThrownBy(() -> servicio.buscar("banana"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(3600));
        assertThat(servidor.recibidas()).hasSize(1);
        assertThat(estado.segundosHastaDisponible(ProveedorExterno.USDA)).isBetween(3599L, 3600L);
    }

    @Test
    void dosErroresSeguidosLoMarcanCaido60Segundos() {
        servidor.encolar(Respuesta.estado(500), Respuesta.estado(500));

        assertThatThrownBy(() -> servicio.detalle("173944"))
                .isInstanceOfSatisfying(ProveedorNoDisponibleException.class,
                        e -> assertThat(e.getSegundosReintento()).isEqualTo(60));
        assertThat(servidor.recibidas()).hasSize(2);
    }

    @Test
    void sinClaveNoSeLlama() {
        FoodDataCentralServicio sinClave = servicio(null);

        assertThat(sinClave.configurado()).isFalse();
        assertThatThrownBy(() -> sinClave.buscar("banana")).isInstanceOf(ProveedorNoDisponibleException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    @Test
    void entradasNoValidasSeRechazanSinLlamar() {
        assertThatThrownBy(() -> servicio.buscar("a")).isInstanceOf(PeticionInvalidaException.class);
        assertThatThrownBy(() -> servicio.detalle("173944&api_key=otra")).isInstanceOf(PeticionInvalidaException.class);
        assertThat(servidor.recibidas()).isEmpty();
    }

    private FoodDataCentralServicio servicio(String clave) {
        IntegracionProperties propiedades = new IntegracionProperties(null, null,
                new IntegracionProperties.Proveedor(servidor.url() + "/fdc/v1", null, null, null, null, clave, 1000, 300));
        return new FoodDataCentralServicio(RestClient.builder(), propiedades, new LimitadorProveedor(), estado,
                Clock.systemUTC(), Duration.ZERO);
    }
}
