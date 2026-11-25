package com.nutrisport.menus.web;

import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.menus.dto.ActualizarMenuRequest;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.dto.MenuResumenResponse;
import com.nutrisport.menus.servicio.MenuServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Menús", description = "Menús planificados por días con recetas y raciones: lo planificado frente al "
        + "objetivo de cada día (incluidos los entrenamientos), favoritos y estado.")
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

    private final MenuServicio menuServicio;

    public MenuController(MenuServicio menuServicio) {
        this.menuServicio = menuServicio;
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
