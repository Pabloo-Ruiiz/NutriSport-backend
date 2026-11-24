package com.nutrisport.ia.adaptador;

import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import com.nutrisport.ia.puerto.AlimentoDetectado;
import com.nutrisport.ia.puerto.ContextoReconocimiento;
import com.nutrisport.ia.puerto.ReconocimientoAlimentosPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * Reconocimiento de alimentos con un LLM multimodal a través de Spring AI (§4.3). Solo existe con
 * {@code app.ia.modo=real}. Envía la foto (ya sin EXIF), el tipo de comida y la descripción del usuario,
 * nunca datos personales (§8.3), y pide salida estructurada: una lista de {@link AlimentoDetectado}.
 * <p>
 * La respuesta se sanea antes de usarla: se descartan los elementos sin nombre o con gramos ≤ 0 o
 * > 2000, la confianza se limita a [0, 1], la preparación ausente pasa a OTRA y se toman como máximo 8.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "real")
public class SpringAiReconocimientoAdaptador implements ReconocimientoAlimentosPuerto {

    static final String PLANTILLA = "reconocimiento-alimentos";
    static final int MAXIMO_ALIMENTOS = 8;
    static final BigDecimal MAXIMO_GRAMOS = new BigDecimal("2000");

    /** Marcas que delimitan la descripción en el prompt; se quitan de lo que escribe el usuario. */
    private static final Pattern MARCAS = Pattern.compile("</?\\s*descripcion_usuario\\s*>", Pattern.CASE_INSENSITIVE);
    private static final ParameterizedTypeReference<List<AlimentoDetectado>> LISTA = new ParameterizedTypeReference<>() {
    };

    private final ChatClient chatClient;

    public SpringAiReconocimientoAdaptador(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ResultadoIa<List<AlimentoDetectado>> reconocer(byte[] jpeg, ContextoReconocimiento contexto) {
        String texto = new PromptTemplate(Plantillas.cargar(PLANTILLA)).render(Map.of(
                "tipoComida", contexto.tipoComida() == null ? "no indicado" : contexto.tipoComida().name(),
                "descripcion", descripcionSegura(contexto.descripcionUsuario())));
        ResponseEntity<ChatResponse, List<AlimentoDetectado>> respuesta = LlamadaConReintento.ejecutar(
                () -> chatClient.prompt()
                        .user(usuario -> usuario.text(texto).media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(jpeg)))
                        .call()
                        .responseEntity(LISTA),
                "plantilla " + PLANTILLA);
        if (respuesta == null) {
            throw new ServicioNoDisponibleException(LlamadaConReintento.NO_DISPONIBLE);
        }
        UsoTokens uso = UsoTokens.de(respuesta.response());
        return new ResultadoIa<>(sanear(respuesta.entity()), uso.entrada(), uso.salida(), uso.modelo());
    }

    /** Sin las marcas de delimitación, para que el usuario no pueda "cerrar" el bloque de datos. */
    static String descripcionSegura(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return "(sin descripción)";
        }
        return MARCAS.matcher(descripcion).replaceAll(" ").strip();
    }

    static List<AlimentoDetectado> sanear(List<AlimentoDetectado> detectados) {
        if (detectados == null) {
            return List.of();
        }
        return detectados.stream()
                .filter(Objects::nonNull)
                .filter(alimento -> alimento.nombre() != null && !alimento.nombre().isBlank())
                .filter(alimento -> alimento.gramosEstimados() != null
                        && alimento.gramosEstimados().signum() > 0
                        && alimento.gramosEstimados().compareTo(MAXIMO_GRAMOS) <= 0)
                .limit(MAXIMO_ALIMENTOS)
                .map(alimento -> new AlimentoDetectado(
                        alimento.nombre().strip(),
                        alimento.preparacion() == null ? Preparacion.OTRA : alimento.preparacion(),
                        alimento.gramosEstimados(),
                        limitarConfianza(alimento.confianza()),
                        alimento.alternativas()))
                .toList();
    }

    private static BigDecimal limitarConfianza(BigDecimal confianza) {
        if (confianza == null || confianza.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return confianza.min(BigDecimal.ONE);
    }
}
