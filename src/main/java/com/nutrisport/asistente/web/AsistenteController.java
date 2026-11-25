package com.nutrisport.asistente.web;

import com.nutrisport.asistente.dto.ActualizarConversacionRequest;
import com.nutrisport.asistente.dto.ConversacionResponse;
import com.nutrisport.asistente.dto.ConversacionResumenResponse;
import com.nutrisport.asistente.dto.EnviarMensajeRequest;
import com.nutrisport.asistente.dto.MensajeResponse;
import com.nutrisport.asistente.dto.ValoracionRequest;
import com.nutrisport.asistente.servicio.AsistenteServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistente nutricional del deportista autenticado (§3.H y §7.9, Ampliación). Responde con los datos del usuario
 * mediante herramientas de solo lectura, así que con el consentimiento de salud revocado responde 403
 * CONSENTIMIENTO_REVOCADO (WebConfig). Las conversaciones y los mensajes de otro usuario, 404. La respuesta es
 * completa (el streaming es Visión).
 */
@RestController
@RequestMapping("/api/v1/asistente")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Asistente", description = "Conversaciones con el asistente nutricional: responde en castellano con los "
        + "datos reales del usuario, que consulta con herramientas de solo lectura (resumen diario, comidas, "
        + "entrenamientos, menú y tendencias); los temas sensibles reciben una respuesta segura sin llamar al modelo.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AsistenteController {

    private static final String EJEMPLO_MENSAJE = """
            {
              "contenido": "¿Cuánta proteína tomé ayer?"
            }""";

    private static final String EJEMPLO_ACTUALIZAR = """
            {
              "titulo": "Proteína y recuperación",
              "archivada": false
            }""";

    private static final String EJEMPLO_VALORACION = """
            {
              "valor": 1
            }""";

    private final AsistenteServicio asistenteServicio;

    public AsistenteController(AsistenteServicio asistenteServicio) {
        this.asistenteServicio = asistenteServicio;
    }

    @GetMapping("/conversaciones")
    @Operation(summary = "Mis conversaciones",
            description = "La del último mensaje más reciente primero (las que aún no tienen mensajes, al final), con "
                    + "una vista previa del último mensaje. Como mucho 50 por página.")
    @ApiResponse(responseCode = "200", description = "Conversaciones del usuario")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<ConversacionResumenResponse> listar(
            @Parameter(description = "Solo las archivadas (true) o solo las activas (false)", example = "false")
            @RequestParam(required = false) Boolean archivada,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return asistenteServicio.listar(archivada, paginacion);
    }

    @PostMapping("/conversaciones")
    @Operation(summary = "Nueva conversación",
            description = "Conversación vacía; su título se rellena con el primer mensaje.")
    @ApiResponse(responseCode = "201", description = "Conversación creada; Location apunta a ella")
    public ResponseEntity<ConversacionResponse> crear() {
        ConversacionResponse creada = asistenteServicio.crear();
        return ResponseEntity.created(URI.create("/api/v1/asistente/conversaciones/" + creada.id())).body(creada);
    }

    @GetMapping("/conversaciones/{id}")
    @Operation(summary = "Mensajes de una conversación",
            description = "Todos los mensajes en orden cronológico, con las herramientas que usó el asistente en cada "
                    + "respuesta y su valoración.")
    @ApiResponse(responseCode = "200", description = "La conversación")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ConversacionResponse obtener(@PathVariable UUID id) {
        return asistenteServicio.obtener(id);
    }

    @PatchMapping("/conversaciones/{id}")
    @Operation(summary = "Renombrar o archivar una conversación",
            description = "Solo cambian los campos presentes.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ActualizarConversacionRequest.class),
                    examples = @ExampleObject(name = "Conversación", summary = "Renombrar",
                            value = EJEMPLO_ACTUALIZAR))))
    @ApiResponse(responseCode = "200", description = "Conversación actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ConversacionResponse actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarConversacionRequest peticion) {
        return asistenteServicio.actualizar(id, peticion);
    }

    @DeleteMapping("/conversaciones/{id}")
    @Operation(summary = "Eliminar una conversación", description = "La borra con todos sus mensajes.")
    @ApiResponse(responseCode = "204", description = "Conversación eliminada")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        asistenteServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversaciones/{id}/mensajes")
    @Operation(summary = "Enviar un mensaje al asistente",
            description = "Guarda el mensaje y devuelve la respuesta completa del asistente, que consulta los datos del "
                    + "usuario con herramientas de solo lectura (herramientasUsadas). Los mensajes sobre conductas "
                    + "alimentarias de riesgo, dosis de medicamentos o suplementos y consultas médicas reciben una "
                    + "respuesta segura sin llamar al modelo. El primer mensaje da título a la conversación. 429 si "
                    + "se ha alcanzado el límite diario de mensajes; 403 EMAIL_NO_VERIFICADO; 503 si la IA no "
                    + "responde (el mensaje queda guardado sin respuesta).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EnviarMensajeRequest.class),
                    examples = @ExampleObject(name = "Pregunta", summary = "Consulta sobre los datos del usuario",
                            value = EJEMPLO_MENSAJE))))
    @ApiResponse(responseCode = "201", description = "Respuesta del asistente")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public ResponseEntity<MensajeResponse> enviarMensaje(@PathVariable UUID id,
            @Valid @RequestBody EnviarMensajeRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asistenteServicio.enviarMensaje(id, peticion));
    }

    @PostMapping("/mensajes/{mensajeId}/valoracion")
    @Operation(summary = "Valorar una respuesta",
            description = "1 (útil) o -1 (no útil); se puede cambiar. Solo las respuestas del asistente (422 si no).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ValoracionRequest.class),
                    examples = @ExampleObject(name = "Útil", summary = "Pulgar arriba", value = EJEMPLO_VALORACION))))
    @ApiResponse(responseCode = "204", description = "Valoración guardada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public ResponseEntity<Void> valorar(@PathVariable UUID mensajeId, @Valid @RequestBody ValoracionRequest peticion) {
        asistenteServicio.valorar(mensajeId, peticion.valor());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sugerencias")
    @Operation(summary = "Preguntas sugeridas",
            description = "3 o 4 preguntas para empezar según la hora del día y si hay entrenamiento hoy.")
    @ApiResponse(responseCode = "200", description = "Preguntas sugeridas")
    public List<String> sugerencias() {
        return asistenteServicio.sugerencias();
    }
}
