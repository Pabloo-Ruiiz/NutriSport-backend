package com.nutrisport.ia.adaptador.simulado;

import com.nutrisport.asistente.herramientas.HerramientasAsistente;
import com.nutrisport.asistente.herramientas.HerramientasAsistente.ResumenDia;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.ia.puerto.ChatAsistentePuerto;
import com.nutrisport.ia.puerto.ContextoChat;
import com.nutrisport.ia.puerto.RespuestaChat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Asistente sin proveedor de IA (modo {@code simulado}, por defecto): para desarrollo y tests. Si el mensaje habla de
 * proteína (con o sin tilde), consulta la herramienta real {@link HerramientasAsistente#resumenDiario} de ayer y de hoy
 * con el mismo contexto que usaría el modelo y responde con esas cifras; si no, con un texto fijo. Sin tokens.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "simulado", matchIfMissing = true)
public class ChatAsistenteSimulado implements ChatAsistentePuerto {

    static final String MODELO = "simulado";
    static final String RESPUESTA_FIJA = "Soy el asistente en modo simulado: ahora mismo solo puedo decirte cuánta "
            + "proteína has tomado ayer y hoy. Mis recomendaciones son orientativas y no sustituyen el consejo de un "
            + "profesional.";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HerramientasAsistente herramientas;
    private final Clock reloj;

    public ChatAsistenteSimulado(HerramientasAsistente herramientas, Clock reloj) {
        this.herramientas = herramientas;
        this.reloj = reloj;
    }

    @Override
    public RespuestaChat responder(ContextoChat contexto) {
        if (!TextoNormalizado.normalizar(contexto.mensaje()).contains("proteina")) {
            return new RespuestaChat(RESPUESTA_FIJA, List.of(), 0, 0, MODELO);
        }
        List<String> usadas = new CopyOnWriteArrayList<>();
        ToolContext contextoHerramientas = new ToolContext(Map.of(
                HerramientasAsistente.CLAVE_USUARIO, contexto.usuarioId(),
                HerramientasAsistente.CLAVE_HERRAMIENTAS, usadas));
        LocalDate hoy = LocalDate.now(reloj);
        ResumenDia ayer = herramientas.resumenDiario(hoy.minusDays(1), contextoHerramientas);
        ResumenDia deHoy = herramientas.resumenDiario(hoy, contextoHerramientas);
        String texto = "Ayer (" + ayer.fecha().format(FECHA) + ") tomaste " + proteina(ayer) + ". Hoy llevas "
                + proteina(deHoy) + ".";
        return new RespuestaChat(texto, usadas, 0, 0, MODELO);
    }

    /** "46,6 g de proteína" y, si hay objetivo, " de un objetivo de 97 g". */
    private static String proteina(ResumenDia dia) {
        String texto = gramos(dia.consumido().proteina()) + " g de proteína";
        return dia.objetivo() == null ? texto : texto + " de un objetivo de " + gramos(dia.objetivo().proteina())
                + " g";
    }

    /** Con un decimal como mucho y coma decimal: 46.6 → "46,6"; 97.0 → "97". */
    static String gramos(BigDecimal valor) {
        return valor.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
