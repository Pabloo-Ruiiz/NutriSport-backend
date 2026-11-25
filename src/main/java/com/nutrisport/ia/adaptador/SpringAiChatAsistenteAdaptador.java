package com.nutrisport.ia.adaptador;

import com.nutrisport.asistente.entidad.MensajeIa;
import com.nutrisport.asistente.entidad.RolMensaje;
import com.nutrisport.asistente.herramientas.HerramientasAsistente;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.ia.puerto.ChatAsistentePuerto;
import com.nutrisport.ia.puerto.ContextoChat;
import com.nutrisport.ia.puerto.RespuestaChat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Asistente con el proveedor real (§4.4), solo con {@code app.ia.modo=real}: {@link ChatClient} con el prompt de
 * sistema {@code prompts/asistente-sistema.st} (perfil resumido, restricciones y la fecha de hoy), los últimos 12
 * mensajes como historial y las herramientas de solo lectura de {@link HerramientasAsistente}.
 * <p>
 * El usuario de las herramientas va en el contexto de herramientas ({@code toolContext}), que el modelo no ve ni
 * puede cambiar; también una lista segura entre hilos donde cada herramienta anota su nombre. La llamada sigue la
 * política de {@link LlamadaConReintento}; si se reintenta, la lista se vacía antes para no contar dos veces.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class SpringAiChatAsistenteAdaptador implements ChatAsistentePuerto {

    static final String PLANTILLA_SISTEMA = "asistente-sistema";
    static final int MENSAJES_HISTORIAL = 12;

    private final ChatClient chatClient;
    private final HerramientasAsistente herramientas;
    private final Clock reloj;

    public SpringAiChatAsistenteAdaptador(ChatClient chatClient, HerramientasAsistente herramientas, Clock reloj) {
        this.chatClient = chatClient;
        this.herramientas = herramientas;
        this.reloj = reloj;
    }

    @Override
    public RespuestaChat responder(ContextoChat contexto) {
        String sistema = new PromptTemplate(Plantillas.cargar(PLANTILLA_SISTEMA)).render(Map.of(
                "perfil", contexto.perfilResumido() == null ? "sin perfil" : contexto.perfilResumido(),
                "restricciones", contexto.restricciones() == null ? "ninguna" : contexto.restricciones(),
                "hoy", LocalDate.now(reloj).toString()));
        List<Message> historial = historial(contexto.historial());
        List<String> usadas = new CopyOnWriteArrayList<>();
        Map<String, Object> contextoHerramientas = Map.of(
                HerramientasAsistente.CLAVE_USUARIO, contexto.usuarioId(),
                HerramientasAsistente.CLAVE_HERRAMIENTAS, usadas);

        ChatResponse respuesta = LlamadaConReintento.ejecutar(() -> {
            usadas.clear();
            return chatClient.prompt()
                    .system(sistema)
                    .messages(historial)
                    .user(contexto.mensaje())
                    .tools(herramientas)
                    .toolContext(contextoHerramientas)
                    .call()
                    .chatResponse();
        }, "asistente");
        if (respuesta == null || respuesta.getResult() == null || respuesta.getResult().getOutput() == null) {
            throw new ServicioNoDisponibleException(LlamadaConReintento.NO_DISPONIBLE);
        }
        UsoTokens uso = UsoTokens.de(respuesta);
        return new RespuestaChat(respuesta.getResult().getOutput().getText(), List.copyOf(usadas), uso.entrada(),
                uso.salida(), uso.modelo());
    }

    /** Los últimos 12 mensajes de usuario y asistente (los de herramienta no se reenvían). */
    static List<Message> historial(List<MensajeIa> mensajes) {
        List<MensajeIa> ultimos = mensajes.subList(Math.max(0, mensajes.size() - MENSAJES_HISTORIAL), mensajes.size());
        return ultimos.stream()
                .filter(mensaje -> mensaje.getRol() != RolMensaje.HERRAMIENTA)
                .map(mensaje -> mensaje.getRol() == RolMensaje.USUARIO
                        ? (Message) new UserMessage(mensaje.getContenido())
                        : new AssistantMessage(mensaje.getContenido()))
                .toList();
    }
}
