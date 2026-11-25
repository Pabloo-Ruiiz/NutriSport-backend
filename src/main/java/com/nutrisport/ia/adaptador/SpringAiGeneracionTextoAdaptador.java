package com.nutrisport.ia.adaptador;

import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generación de texto con el proveedor real a través de Spring AI (§5.7). Solo existe con
 * {@code app.ia.modo=real}. Plantillas StringTemplate de {@code src/main/resources/prompts/}; timeout y un
 * único reintento según {@link LlamadaConReintento}.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class SpringAiGeneracionTextoAdaptador implements GeneracionTextoPuerto {

    private final ChatClient chatClient;

    public SpringAiGeneracionTextoAdaptador(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ResultadoIa<String> generar(String plantilla, Map<String, Object> variables) {
        String peticion = new PromptTemplate(Plantillas.cargar(plantilla)).render(variables);
        ChatResponse respuesta = LlamadaConReintento.ejecutar(
                () -> chatClient.prompt().user(peticion).call().chatResponse(), "plantilla " + plantilla);
        if (respuesta == null || respuesta.getResult() == null) {
            throw new ServicioNoDisponibleException(LlamadaConReintento.NO_DISPONIBLE);
        }
        UsoTokens uso = UsoTokens.de(respuesta);
        return new ResultadoIa<>(respuesta.getResult().getOutput().getText(), uso.entrada(), uso.salida(),
                uso.modelo());
    }

    @Override
    public <T> ResultadoIa<T> generarEstructurado(String plantilla, Map<String, Object> variables, Class<T> tipo) {
        String peticion = new PromptTemplate(Plantillas.cargar(plantilla)).render(variables);
        // responseEntity añade al prompt las instrucciones de formato con el esquema JSON del tipo
        ResponseEntity<ChatResponse, T> respuesta = LlamadaConReintento.ejecutar(
                () -> chatClient.prompt().user(peticion).call().responseEntity(tipo), "plantilla " + plantilla);
        if (respuesta == null) {
            throw new ServicioNoDisponibleException(LlamadaConReintento.NO_DISPONIBLE);
        }
        UsoTokens uso = UsoTokens.de(respuesta.response());
        return new ResultadoIa<>(respuesta.entity(), uso.entrada(), uso.salida(), uso.modelo());
    }
}
