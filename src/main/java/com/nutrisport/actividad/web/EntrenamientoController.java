package com.nutrisport.actividad.web;

import com.nutrisport.actividad.dto.EntrenamientoRequest;
import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.dto.TipoActividadResponse;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.servicio.EntrenamientoServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.seguimiento.dto.RelacionNutricionResponse;
import com.nutrisport.seguimiento.servicio.RelacionNutricionActividadServicio;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrenamientos del deportista autenticado (§7.8). El catálogo de tipos es para cualquier usuario
 * autenticado (D/A); el resto, solo para deportistas. Son datos de salud: con el consentimiento revocado
 * responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/entrenamientos")
@Tag(name = "Actividad", description = "Entrenamientos realizados y planificados con su gasto neto estimado a partir "
        + "del MET, el peso y la duración, y catálogo de tipos de actividad. Cada cambio recalcula el objetivo del día.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class EntrenamientoController {

    private static final String EJEMPLO_ENTRENAMIENTO = """
            {
              "tipoActividadCodigo": "CARRERA_12KMH",
              "fechaHora": "2026-11-24T07:30:00+01:00",
              "duracionMin": 65,
              "intensidad": "MEDIA",
              "rpe": 7,
              "estado": "REALIZADO",
              "distanciaKm": 13.0,
              "notas": "Rodaje con series en la segunda mitad"
            }""";

    private final EntrenamientoServicio entrenamientoServicio;

    private final RelacionNutricionActividadServicio relacionNutricionServicio;

    public EntrenamientoController(EntrenamientoServicio entrenamientoServicio,
            RelacionNutricionActividadServicio relacionNutricionServicio) {
        this.entrenamientoServicio = entrenamientoServicio;
        this.relacionNutricionServicio = relacionNutricionServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Historial y próximos entrenamientos",
            description = "Entrenamientos entre desde y hasta (ambos incluidos, máximo 92 días) en orden "
                    + "cronológico, opcionalmente de un estado.")
    @ApiResponse(responseCode = "200", description = "Entrenamientos del periodo")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public List<EntrenamientoResponse> listar(
            @Parameter(description = "Primer día (incluido)", example = "2026-11-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido); como mucho 92 días después de desde", example = "2026-11-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Solo los entrenamientos en este estado")
            @RequestParam(required = false) EstadoEntrenamiento estado) {
        return entrenamientoServicio.listar(desde, hasta, estado);
    }

    @GetMapping("/relacion-nutricion")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Alimentación en días de entreno frente a descanso",
            description = "Media diaria consumida y objetivo medio en los días con algún entrenamiento planificado o "
                    + "realizado frente a los días sin entrenamiento (solo los días con alguna comida registrada), "
                    + "y cuántas sesiones tienen al menos 25 g de proteína en las comidas con hora de las 2 h "
                    + "siguientes al final del entreno. Rango de como mucho 366 días.")
    @ApiResponse(responseCode = "200", description = "Relación entre alimentación y actividad")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public RelacionNutricionResponse relacionNutricion(
            @Parameter(description = "Primer día (incluido)", example = "2026-10-26")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido); como mucho 366 días contando desde", example = "2026-11-22")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return relacionNutricionServicio.calcular(desde, hasta);
    }

    @GetMapping("/tipos")
    @Operation(summary = "Catálogo de actividades con su MET",
            description = "Tipos de actividad activos, ordenados por categoría y nombre. Su código se usa en "
                    + "tipoActividadCodigo y en el deporte principal del perfil.")
    @ApiResponse(responseCode = "200", description = "Tipos de actividad")
    public List<TipoActividadResponse> tipos() {
        return entrenamientoServicio.tipos();
    }

    @PostMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Registrar o planificar un entrenamiento",
            description = "Duración de 1 a 600 minutos. Sin estado: PLANIFICADO si la fecha es futura y REALIZADO "
                    + "si no (un entrenamiento futuro no puede estar REALIZADO). Calcula el gasto neto estimado con "
                    + "el peso del perfil y recalcula el objetivo del día. 422 PERFIL_REQUERIDO si no hay perfil.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EntrenamientoRequest.class),
                    examples = @ExampleObject(name = "Entrenamiento", summary = "Rodaje de 65 minutos ya realizado",
                            value = EJEMPLO_ENTRENAMIENTO))))
    @ApiResponse(responseCode = "201", description = "Entrenamiento registrado; Location apunta a él")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public ResponseEntity<EntrenamientoResponse> crear(@Valid @RequestBody EntrenamientoRequest peticion) {
        EntrenamientoResponse creado = entrenamientoServicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/entrenamientos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Editar un entrenamiento (incluido marcarlo como realizado)",
            description = "Sustituye sus datos; sin estado se conserva el actual. Recalcula el gasto y los "
                    + "objetivos de los días afectados. 422 PERFIL_REQUERIDO si no hay perfil.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EntrenamientoRequest.class),
                    examples = @ExampleObject(name = "Entrenamiento", summary = "Rodaje de 65 minutos ya realizado",
                            value = EJEMPLO_ENTRENAMIENTO))))
    @ApiResponse(responseCode = "200", description = "Entrenamiento actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public EntrenamientoResponse editar(@PathVariable UUID id, @Valid @RequestBody EntrenamientoRequest peticion) {
        return entrenamientoServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar un entrenamiento", description = "Recalcula el objetivo de su día.")
    @ApiResponse(responseCode = "204", description = "Entrenamiento eliminado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        entrenamientoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
