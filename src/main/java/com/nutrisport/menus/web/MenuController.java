package com.nutrisport.menus.web;

import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.menus.dto.ActualizarMenuRequest;
import com.nutrisport.menus.dto.AplicarSugerenciaRequest;
import com.nutrisport.menus.dto.GenerarMenuRequest;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.dto.MenuResumenResponse;
import com.nutrisport.menus.dto.SugerenciaIaRequest;
import com.nutrisport.menus.dto.SugerenciaIaResponse;
import com.nutrisport.menus.servicio.GeneradorMenuServicio;
import com.nutrisport.menus.servicio.MenuServicio;
import com.nutrisport.menus.servicio.SugerenciaIaServicio;
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
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Menús del deportista autenticado (§3.D y §7.5, Ampliación). Se calculan con el perfil y los entrenamientos, así que
 * con el consentimiento revocado responden 403 CONSENTIMIENTO_REVOCADO (WebConfig). Los de otro usuario, 404.
 */
@RestController
@RequestMapping("/api/v1/menus")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Menús", description = "Menús planificados por días con recetas y raciones: generación con un motor de "
        + "reglas determinista, lo planificado frente al objetivo de cada día (incluidos los entrenamientos), "
        + "favoritos, estado y sugerencias de ajuste de la IA validadas por el backend.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class MenuController {

    private static final String EJEMPLO_ACTUALIZAR = """
            {
              "nombre": "Semana de carga",
              "favorito": true,
              "estado": "ACTIVO"
            }""";

    private static final String EJEMPLO_GENERAR = """
            {
              "tipo": "SEMANAL",
              "fechaInicio": "2026-11-30",
              "nombre": "Semana de carga",
              "conExplicacion": true
            }""";

    private static final String EJEMPLO_SUGERENCIA = """
            {
              "preferencia": "cambia la nata por yogur sin lactosa"
            }""";

    private static final String EJEMPLO_APLICAR = """
            {
              "sugerenciaId": "0195f3a2-7c1e-7b8a-9d40-2f6a1c3e5b70"
            }""";

    private final MenuServicio menuServicio;
    private final GeneradorMenuServicio generadorMenuServicio;
    private final SugerenciaIaServicio sugerenciaIaServicio;

    public MenuController(MenuServicio menuServicio, GeneradorMenuServicio generadorMenuServicio,
            SugerenciaIaServicio sugerenciaIaServicio) {
        this.menuServicio = menuServicio;
        this.generadorMenuServicio = generadorMenuServicio;
        this.sugerenciaIaServicio = sugerenciaIaServicio;
    }

    @PostMapping("/generar")
    @Operation(summary = "Generar un menú",
            description = "Genera con el motor de reglas (no con IA) un menú DIARIO (1 día) o SEMANAL (7 días "
                    + "consecutivos; de lunes a domingo si empieza en lunes) desde fechaInicio, que no puede ser "
                    + "anterior a hoy. Solo usa recetas publicadas, públicas o propias, con cálculo COMPLETO o "
                    + "APROXIMADO, aptas para cada franja, compatibles con las restricciones estrictas y dentro del "
                    + "tiempo máximo de cocina. Cada día se ajusta a su objetivo (con los entrenamientos "
                    + "planificados) en ±10 % de kcal y ±15 % de proteína o lleva un aviso en la explicación. Con "
                    + "conExplicacion (por defecto), la IA redacta una explicación breve con datos ya calculados; si "
                    + "falla, se usa una de plantilla. El menú se guarda ACTIVO con generadoAutomaticamente = true. "
                    + "422 PERFIL_REQUERIDO sin perfil; 422 SIN_RECETAS_COMPATIBLES si ninguna receta encaja.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = GenerarMenuRequest.class),
                    examples = @ExampleObject(name = "Semanal", summary = "Menú de lunes a domingo",
                            value = EJEMPLO_GENERAR))))
    @ApiResponse(responseCode = "201", description = "Menú generado; Location apunta a él")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public ResponseEntity<MenuResponse> generar(@Valid @RequestBody GenerarMenuRequest peticion) {
        MenuResponse generado = generadorMenuServicio.generar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/menus/" + generado.id())).body(generado);
    }

    @GetMapping
    @Operation(summary = "Mis menús",
            description = "Resumen de los menús, el que empieza más tarde primero (como mucho 50 por página).")
    @ApiResponse(responseCode = "200", description = "Menús del usuario")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<MenuResumenResponse> listar(
            @Parameter(description = "Solo los favoritos (true) o solo los que no lo son (false)", example = "true")
            @RequestParam(required = false) Boolean favorito,
            @Parameter(description = "Solo los menús que terminan este día o después", example = "2026-11-23")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return menuServicio.listar(favorito, desde, paginacion);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un menú",
            description = "Todos los días del menú con sus comidas (nutrientes = por ración × raciones), lo "
                    + "planificado frente al objetivo del día (porcentajeObjetivo) y si es día de entreno. Sin perfil, "
                    + "objetivo es null y porcentajeObjetivo 0. Las recetas sin cálculo nutricional no suman.")
    @ApiResponse(responseCode = "200", description = "Menú con sus días")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public MenuResponse obtener(@PathVariable UUID id) {
        return menuServicio.obtener(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar nombre, favorito o estado",
            description = "Solo cambian los campos presentes; devuelve el menú completo.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ActualizarMenuRequest.class),
                    examples = @ExampleObject(name = "Menú", summary = "Renombrar y marcar como favorito",
                            value = EJEMPLO_ACTUALIZAR))))
    @ApiResponse(responseCode = "200", description = "Menú actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public MenuResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarMenuRequest peticion) {
        return menuServicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/comidas/{comidaId}/sugerencia-ia")
    @Operation(summary = "Sugerencia IA para una comida",
            description = "La IA propone UN ajuste de cantidad o una sustitución de un ingrediente de la receta "
                    + "(recibe los ingredientes con gramos, el hueco energético y proteico del día y las "
                    + "restricciones, nunca datos personales). El backend busca el alimento en el catálogo, comprueba "
                    + "restricciones y alérgenos y calcula las cifras (deltas por ración); si el alimento no existe o "
                    + "no cumple, valida = false y el texto dice por qué. 422 PERFIL_REQUERIDO sin perfil; 503 si la "
                    + "IA no responde.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SugerenciaIaRequest.class),
                    examples = @ExampleObject(name = "Preferencia", summary = "Sustituir un lácteo",
                            value = EJEMPLO_SUGERENCIA))))
    @ApiResponse(responseCode = "200", description = "Sugerencia validada (o descartada, con valida = false)")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public SugerenciaIaResponse sugerir(@PathVariable UUID id, @PathVariable UUID comidaId,
            @Valid @RequestBody SugerenciaIaRequest peticion) {
        return sugerenciaIaServicio.sugerirAjuste(id, comidaId, peticion.preferencia());
    }

    @PostMapping("/{id}/comidas/{comidaId}/sugerencia-ia/aplicar")
    @Operation(summary = "Aplicar una sugerencia IA",
            description = "Crea una receta PROPIA derivada (copia privada con el ajuste y sus nutrientes "
                    + "recalculados) y la pone en la comida; devuelve el menú. 422 SUGERENCIA_NO_VALIDA si se "
                    + "descartó o el alimento ya no cumple; 409 SUGERENCIA_APLICADA, COMIDA_CONSUMIDA o "
                    + "SUGERENCIA_OBSOLETA (la comida ya tiene otra receta).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AplicarSugerenciaRequest.class),
                    examples = @ExampleObject(name = "Sugerencia", summary = "La devuelta por sugerencia-ia",
                            value = EJEMPLO_APLICAR))))
    @ApiResponse(responseCode = "200", description = "Menú con la comida cambiada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public MenuResponse aplicarSugerencia(@PathVariable UUID id, @PathVariable UUID comidaId,
            @Valid @RequestBody AplicarSugerenciaRequest peticion) {
        return sugerenciaIaServicio.aplicar(id, comidaId, peticion.sugerenciaId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un menú",
            description = "Lo borra con sus comidas planificadas; las comidas ya registradas se conservan.")
    @ApiResponse(responseCode = "204", description = "Menú eliminado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        menuServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
