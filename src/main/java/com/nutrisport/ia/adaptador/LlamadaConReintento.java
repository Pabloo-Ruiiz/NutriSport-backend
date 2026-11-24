package com.nutrisport.ia.adaptador;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Política común de las llamadas al proveedor de IA (§5.7), sin librerías de circuit breaker (§5.11):
 * un único reintento, solo ante error de red, timeout o respuesta 5xx; nunca ante 4xx ni 429. Si no hay
 * respuesta → 503 SERVICIO_NO_DISPONIBLE. El timeout lo aplica el cliente del proveedor
 * ({@code spring.ai.anthropic.timeout}, tomado de {@code app.ia.timeout-segundos}), que no reintenta por
 * su cuenta ({@code max-retries=0}).
 */
public final class LlamadaConReintento {

    static final String NO_DISPONIBLE = "El servicio de IA no está disponible";

    private static final Logger LOG = LoggerFactory.getLogger(LlamadaConReintento.class);

    private LlamadaConReintento() {
    }

    /**
     * @param llamada     la petición al proveedor
     * @param descripcion para el log (p. ej. la plantilla); nunca datos del usuario
     */
    public static <T> T ejecutar(Supplier<T> llamada, String descripcion) {
        try {
            return llamada.get();
        } catch (RuntimeException primerFallo) {
            if (!esReintentable(primerFallo)) {
                throw noDisponible(descripcion, primerFallo);
            }
            LOG.info("Fallo transitorio del proveedor de IA ({}); se reintenta una vez: {}", descripcion,
                    primerFallo.getMessage());
            try {
                return llamada.get();
            } catch (RuntimeException segundoFallo) {
                throw noDisponible(descripcion, segundoFallo);
            }
        }
    }

    /**
     * Reintentable: error de red o timeout, o respuesta 5xx. No lo es un 4xx (petición mal formada,
     * clave no válida...) ni un 429 (reintentar al momento lo empeora). Se recorre la cadena de causas
     * porque Spring AI puede envolver la excepción del SDK.
     */
    static boolean esReintentable(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof AnthropicServiceException servicio) {
                return servicio.statusCode() >= 500;
            }
            if (causa instanceof AnthropicIoException || causa instanceof IOException
                    || causa instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static ServicioNoDisponibleException noDisponible(String descripcion, RuntimeException causa) {
        LOG.warn("El proveedor de IA no ha respondido ({}): {}", descripcion, causa.toString());
        return new ServicioNoDisponibleException(NO_DISPONIBLE);
    }
}
