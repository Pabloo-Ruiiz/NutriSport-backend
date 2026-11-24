package com.nutrisport.ia.adaptador;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Generación de texto con el proveedor real a través de Spring AI (§5.7). Solo existe con
 * {@code app.ia.modo=real}.
 * <ul>
 * <li>Plantillas StringTemplate de {@code src/main/resources/prompts/}.</li>
 * <li>Timeout: el del cliente del proveedor, configurado con {@code app.ia.timeout-segundos}
 * ({@code spring.ai.anthropic.timeout}).</li>
 * <li>Un único reintento, solo ante error de red, timeout o respuesta 5xx; nunca ante 4xx ni 429 (el
 * cliente tiene {@code max-retries=0} para que nadie más reintente). Sin circuit breaker (§5.11).</li>
 * <li>Si no hay respuesta → 503 SERVICIO_NO_DISPONIBLE.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class SpringAiGeneracionTextoAdaptador implements GeneracionTextoPuerto {

    static final String NO_DISPONIBLE = "El servicio de IA no está disponible";

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiGeneracionTextoAdaptador.class);
    /** Solo nombres simples: evita leer ficheros fuera de prompts/ con "../". */
    private static final Pattern NOMBRE_PLANTILLA = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private final ChatClient chatClient;

    public SpringAiGeneracionTextoAdaptador(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ResultadoIa<String> generar(String plantilla, Map<String, Object> variables) {
        String peticion = new PromptTemplate(cargar(plantilla)).render(variables);
        ChatResponse respuesta;
        try {
            respuesta = llamar(peticion);
        } catch (RuntimeException primerFallo) {
            if (!esReintentable(primerFallo)) {
                throw noDisponible(plantilla, primerFallo);
            }
            LOG.info("Fallo transitorio del proveedor de IA con la plantilla {}; se reintenta una vez: {}",
                    plantilla, primerFallo.getMessage());
            try {
                respuesta = llamar(peticion);
            } catch (RuntimeException segundoFallo) {
                throw noDisponible(plantilla, segundoFallo);
            }
        }
        return resultado(respuesta);
    }

    private ChatResponse llamar(String peticion) {
        return chatClient.prompt().user(peticion).call().chatResponse();
    }

    private static Resource cargar(String plantilla) {
        if (plantilla == null || !NOMBRE_PLANTILLA.matcher(plantilla).matches()) {
            throw new IllegalArgumentException("Nombre de plantilla no válido: " + plantilla);
        }
        Resource recurso = new ClassPathResource("prompts/" + plantilla + ".st");
        if (!recurso.exists()) {
            throw new IllegalArgumentException("No existe la plantilla prompts/" + plantilla + ".st");
        }
        return recurso;
    }

    /** Texto de la respuesta y, si el proveedor los informa, tokens y modelo. */
    private static ResultadoIa<String> resultado(ChatResponse respuesta) {
        if (respuesta == null || respuesta.getResult() == null) {
            throw new ServicioNoDisponibleException(NO_DISPONIBLE);
        }
        String texto = respuesta.getResult().getOutput().getText();
        ChatResponseMetadata metadatos = respuesta.getMetadata();
        Usage uso = metadatos == null ? null : metadatos.getUsage();
        return new ResultadoIa<>(texto,
                uso == null ? 0 : valor(uso.getPromptTokens()),
                uso == null ? 0 : valor(uso.getCompletionTokens()),
                metadatos == null ? null : metadatos.getModel());
    }

    private static int valor(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    /**
     * Reintentable: error de red o timeout, o respuesta 5xx del proveedor. No lo es un 4xx (petición
     * mal formada, clave no válida...) ni un 429 (límite de peticiones: reintentar al momento lo empeora).
     * Se recorre la cadena de causas porque Spring AI puede envolver la excepción del SDK.
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

    private static ServicioNoDisponibleException noDisponible(String plantilla, RuntimeException causa) {
        LOG.warn("El proveedor de IA no ha respondido con la plantilla {}: {}", plantilla, causa.toString());
        return new ServicioNoDisponibleException(NO_DISPONIBLE);
    }
}
