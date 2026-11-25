package com.nutrisport.integracion.usda;

import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.integracion.comun.EstadoProveedor;
import com.nutrisport.integracion.comun.IntegracionProperties;
import com.nutrisport.integracion.comun.LimitadorProveedor;
import com.nutrisport.integracion.comun.LlamadaProveedor;
import com.nutrisport.integracion.comun.OperacionProveedor;
import com.nutrisport.integracion.comun.ProveedorExterno;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente de USDA FoodData Central (docs/API_INTEGRATIONS.md §5): búsqueda ({@code /foods/search}) y detalle
 * ({@code /food/{fdcId}}) de alimentos genéricos Foundation y SR Legacy, para calcular los nutrientes de las
 * recetas. La clave va en el parámetro {@code api_key}; {@link LlamadaProveedor} nunca registra URLs, así que no
 * acaba en los logs.
 * <p>
 * Reglas comunes de {@link LlamadaProveedor} (cupo propio de 900/h, un reintento ante timeout o 5xx, caída de
 * 60 s), salvo el 429: USDA bloquea la clave una hora al superar las 1.000 peticiones por hora, así que el
 * proveedor queda caído una hora.
 */
@Service
public class FoodDataCentralServicio {

    /** USDA bloquea la clave una hora cuando se supera el límite (§5.3). */
    static final Duration CAIDA_POR_LIMITE = Duration.ofHours(1);
    static final String TIPOS_DE_DATOS = "Foundation,SR Legacy";
    static final int TAMANO_PAGINA = 5;
    static final int LONGITUD_MINIMA = 2;
    static final int LONGITUD_MAXIMA = 100;

    private static final Pattern FDC_ID = Pattern.compile("\\d{1,10}");
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    private final RestClient cliente;
    private final String clave;
    private final boolean configurado;
    private final LlamadaProveedor llamada;

    @Autowired
    public FoodDataCentralServicio(RestClient.Builder constructor, IntegracionProperties propiedades,
            LimitadorProveedor limitador, EstadoProveedor estadoProveedor, Clock reloj) {
        this(constructor, propiedades, limitador, estadoProveedor, reloj, LlamadaProveedor.ESPERA_REINTENTO);
    }

    /** Para los tests: permite acortar la espera antes del reintento. */
    FoodDataCentralServicio(RestClient.Builder constructor, IntegracionProperties propiedades,
            LimitadorProveedor limitador, EstadoProveedor estadoProveedor, Clock reloj, Duration esperaReintento) {
        IntegracionProperties.Proveedor usda = propiedades.de(ProveedorExterno.USDA);
        this.llamada = new LlamadaProveedor(ProveedorExterno.USDA, limitador, estadoProveedor, reloj, esperaReintento,
                CAIDA_POR_LIMITE);
        this.clave = usda.apiKey();
        this.configurado = usda.baseUrl() != null && usda.apiKey() != null;
        RestClient.Builder constructorCliente = constructor.clone()
                .requestFactory(LlamadaProveedor.fabricaConTimeouts(usda));
        if (usda.baseUrl() != null) {
            constructorCliente.baseUrl(usda.baseUrl());
        }
        this.cliente = constructorCliente.build();
    }

    /** Si hay clave configurada (sin ella no se llama a USDA). */
    public boolean configurado() {
        return configurado;
    }

    /**
     * Hasta 5 alimentos Foundation y SR Legacy que coinciden con el texto, en inglés ("chicken breast").
     *
     * @throws ProveedorNoDisponibleException si no hay clave, se ha agotado el cupo o USDA falla
     */
    public List<AlimentoUsdaDto> buscar(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.length() < LONGITUD_MINIMA || limpio.length() > LONGITUD_MAXIMA) {
            throw invalida("texto", "Debe tener entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }
        Map<String, Object> cuerpo = obtener(OperacionProveedor.BUSQUEDA, uri -> uri
                .path("/foods/search")
                .queryParam("api_key", "{clave}")
                .queryParam("query", "{texto}")
                .queryParam("dataType", "{tipos}")
                .queryParam("pageSize", "{tamano}")
                .build(clave, limpio, TIPOS_DE_DATOS, TAMANO_PAGINA));
        return RespuestaUsda.elementos(cuerpo, "foods").stream()
                .map(RespuestaUsda::aAlimento)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Detalle de un alimento con sus nutrientes y sus medidas caseras ({@code format=full}); vacío si no existe.
     *
     * @throws ProveedorNoDisponibleException si no hay clave, se ha agotado el cupo o USDA falla
     */
    public Optional<DetalleUsdaDto> detalle(String fdcId) {
        String limpio = fdcId == null ? "" : fdcId.strip();
        if (!FDC_ID.matcher(limpio).matches()) {
            throw invalida("fdcId", "El identificador de USDA debe ser numérico");
        }
        Map<String, Object> cuerpo = obtener(OperacionProveedor.PRODUCTO, uri -> uri
                .path("/food/{fdcId}")
                .queryParam("api_key", "{clave}")
                .queryParam("format", "full")
                .build(limpio, clave));
        if (cuerpo == null) {
            return Optional.empty();
        }
        AlimentoUsdaDto alimento = RespuestaUsda.aAlimento(cuerpo);
        return alimento == null ? Optional.empty()
                : Optional.of(new DetalleUsdaDto(alimento, RespuestaUsda.porciones(cuerpo)));
    }

    private Map<String, Object> obtener(OperacionProveedor operacion, Function<UriBuilder, URI> uri) {
        if (!configurado) {
            throw llamada.sinConfiguracion("USDA_BASE_URL o USDA_API_KEY");
        }
        return llamada.obtener(operacion, cliente, uri);
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
