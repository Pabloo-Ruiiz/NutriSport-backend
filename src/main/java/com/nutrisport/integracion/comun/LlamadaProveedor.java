package com.nutrisport.integracion.comun;

import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.util.UriBuilder;

/**
 * Llamada GET a un proveedor externo con las reglas comunes de docs/API_INTEGRATIONS.md §6.3 y §6.4, para que
 * todos los clientes (Open Food Facts, TheMealDB, USDA) se comporten igual:
 * <ol>
 * <li>Antes de cada intento se comprueban la disponibilidad del proveedor ({@link EstadoProveedor}) y el cupo
 * propio ({@link LimitadorProveedor}); si no se puede llamar, 503 PROVEEDOR_NO_DISPONIBLE con los segundos de
 * espera.</li>
 * <li>Un timeout, un error de red o un 5xx se reintentan una vez (consumiendo cupo, porque es otra llamada); si
 * vuelven a fallar, el proveedor queda caído 60 s.</li>
 * <li>Un 429 o un 503 no se reintentan: el proveedor queda caído lo que diga {@code Retry-After} (segundos o
 * fecha HTTP; 60 s si no viene; como mucho 1 hora).</li>
 * <li>Otro 4xx distinto de 404 (400, 401...) es un error de configuración que no se arregla reintentando: caído
 * 60 s.</li>
 * <li>Un 404 o un cuerpo que no se puede leer devuelven {@code null} ("sin datos").</li>
 * </ol>
 * Los logs solo incluyen el proveedor, la operación y el código HTTP: nunca la URL (puede llevar una clave),
 * los parámetros (texto del usuario) ni la respuesta.
 */
public final class LlamadaProveedor {

    private static final Logger LOG = LoggerFactory.getLogger(LlamadaProveedor.class);

    public static final Duration CAIDA_POR_DEFECTO = Duration.ofSeconds(60);
    public static final Duration ESPERA_REINTENTO = Duration.ofMillis(250);
    private static final Duration ESPERA_MAXIMA = Duration.ofHours(1);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    /** Resultado de un intento: código HTTP, cabeceras y cuerpo (nulo si no hay o no se pudo leer). */
    private record Respuesta(int estado, HttpHeaders cabeceras, Map<String, Object> cuerpo) {
    }

    private final ProveedorExterno proveedor;
    private final LimitadorProveedor limitador;
    private final EstadoProveedor estadoProveedor;
    private final Clock reloj;
    private final Duration esperaReintento;

    public LlamadaProveedor(ProveedorExterno proveedor, LimitadorProveedor limitador, EstadoProveedor estadoProveedor,
            Clock reloj, Duration esperaReintento) {
        this.proveedor = proveedor;
        this.limitador = limitador;
        this.estadoProveedor = estadoProveedor;
        this.reloj = reloj;
        this.esperaReintento = esperaReintento;
    }

    /** Fábrica de peticiones con los timeouts de conexión y de lectura configurados para el proveedor. */
    public static JdkClientHttpRequestFactory fabricaConTimeouts(IntegracionProperties.Proveedor configuracion) {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(configuracion.timeoutConexionMs()))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofMillis(configuracion.timeoutLecturaMs()));
        return fabrica;
    }

    /** 503 sin llamar, para un proveedor al que le falta configuración (URL, clave, User-Agent...). */
    public ProveedorNoDisponibleException sinConfiguracion(String queFalta) {
        LOG.warn("{} no está configurado (falta {})", proveedor, queFalta);
        return new ProveedorNoDisponibleException(CAIDA_POR_DEFECTO.toSeconds());
    }

    /**
     * Hace la petición GET con las reglas de la clase.
     *
     * @return el cuerpo JSON, o {@code null} si el proveedor responde 404 o algo que no se puede leer
     * @throws ProveedorNoDisponibleException si no se puede llamar o el proveedor falla
     */
    public Map<String, Object> obtener(OperacionProveedor operacion, RestClient cliente, Function<UriBuilder, URI> uri) {
        for (int intento = 1; ; intento++) {
            comprobarDisponible(operacion);
            Respuesta respuesta = llamar(cliente, uri, operacion);

            boolean fallo = respuesta == null || (respuesta.estado() >= 500 && respuesta.estado() != 503);
            if (fallo) {
                if (intento == 1) {
                    esperarAntesDeReintentar();
                    continue;
                }
                throw caido(operacion, respuesta == null ? "sin respuesta" : String.valueOf(respuesta.estado()),
                        CAIDA_POR_DEFECTO);
            }
            if (respuesta.estado() == 429 || respuesta.estado() == 503) {
                throw caido(operacion, String.valueOf(respuesta.estado()), retryAfter(respuesta.cabeceras()));
            }
            if (respuesta.estado() == 404) {
                return null;
            }
            if (respuesta.estado() >= 400) {
                throw caido(operacion, String.valueOf(respuesta.estado()), CAIDA_POR_DEFECTO);
            }
            return respuesta.cuerpo();
        }
    }

    private void comprobarDisponible(OperacionProveedor operacion) {
        if (!estadoProveedor.estaDisponible(proveedor)) {
            throw new ProveedorNoDisponibleException(estadoProveedor.segundosHastaDisponible(proveedor));
        }
        if (!limitador.intentar(proveedor, operacion)) {
            throw new ProveedorNoDisponibleException(limitador.segundosHastaCupo(proveedor, operacion));
        }
    }

    /** Hace la petición; nulo si no hubo respuesta (timeout, conexión rechazada...). */
    private Respuesta llamar(RestClient cliente, Function<UriBuilder, URI> uri, OperacionProveedor operacion) {
        try {
            return cliente.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> leer(respuesta, operacion), true);
        } catch (ResourceAccessException e) {
            LOG.info("{} no responde ({}): {}", proveedor, operacion, e.getClass().getSimpleName());
            return null;
        }
    }

    private Respuesta leer(ConvertibleClientHttpResponse respuesta, OperacionProveedor operacion) throws IOException {
        int estado = respuesta.getStatusCode().value();
        Map<String, Object> cuerpo = null;
        if (estado >= 200 && estado < 300) {
            try {
                cuerpo = respuesta.bodyTo(JSON);
            } catch (RuntimeException e) {
                // JSON mal formado, tipo de contenido inesperado...: el conversor puede lanzar varias excepciones
                LOG.warn("Respuesta de {} con una forma inesperada ({}): se trata como sin datos", proveedor, operacion);
            }
        }
        return new Respuesta(estado, respuesta.getHeaders(), cuerpo);
    }

    private ProveedorNoDisponibleException caido(OperacionProveedor operacion, String motivo, Duration espera) {
        LOG.warn("{} no disponible ({}: {}); no se le llamará durante {} s", proveedor, operacion, motivo,
                espera.toSeconds());
        estadoProveedor.marcarCaido(proveedor, espera);
        return new ProveedorNoDisponibleException(espera.toSeconds());
    }

    /** {@code Retry-After} en segundos o como fecha HTTP; 60 s si falta o no se entiende; como mucho 1 hora. */
    private Duration retryAfter(HttpHeaders cabeceras) {
        String valor = cabeceras.getFirst(HttpHeaders.RETRY_AFTER);
        Duration espera = null;
        if (valor != null && !valor.isBlank()) {
            String limpio = valor.strip();
            try {
                espera = limpio.chars().allMatch(Character::isDigit)
                        ? Duration.ofSeconds(Long.parseLong(limpio))
                        : Duration.between(reloj.instant(),
                                ZonedDateTime.parse(limpio, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (NumberFormatException | DateTimeParseException | ArithmeticException e) {
                espera = null;
            }
        }
        if (espera == null || espera.isNegative() || espera.isZero()) {
            return CAIDA_POR_DEFECTO;
        }
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private void esperarAntesDeReintentar() {
        if (esperaReintento.isZero()) {
            return;
        }
        try {
            Thread.sleep(esperaReintento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProveedorNoDisponibleException(CAIDA_POR_DEFECTO.toSeconds());
        }
    }
}
