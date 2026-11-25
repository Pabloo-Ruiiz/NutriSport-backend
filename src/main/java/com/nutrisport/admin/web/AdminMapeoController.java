package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.ActualizarMapeoRequest;
import com.nutrisport.admin.dto.MapeoIngredienteResponse;
import com.nutrisport.admin.servicio.AdminMapeoServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cola de revisión de los emparejamientos de ingredientes (§7.10, Ampliación). */
@RestController
@RequestMapping("/api/v1/admin/mapeos-ingredientes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Solo para administradores: catálogo de alimentos (alta, edición, revisión de "
        + "alérgenos e importación CSV), categorías, recetas públicas y su publicación, emparejamientos de "
        + "ingredientes y usuarios (búsqueda, bloqueo y roles). Cada acción queda auditada. Nunca da acceso a datos "
        + "de salud.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AdminMapeoController {

    private final AdminMapeoServicio adminMapeoServicio;

    public AdminMapeoController(AdminMapeoServicio adminMapeoServicio) {
        this.adminMapeoServicio = adminMapeoServicio;
    }

    @GetMapping
    @Operation(summary = "Emparejamientos de ingredientes",
            description = "Con revisado=false, la cola de pendientes: emparejamientos automáticos sin revisar, incluidos "
                    + "los de confianza menor de 0,6 que no se usan en los cálculos. Por nombre; máximo 100 por página.")
    @ApiResponse(responseCode = "200", description = "Página de emparejamientos")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<MapeoIngredienteResponse> listar(
            @Parameter(description = "false: pendientes de revisar; true: revisados; sin él, todos", example = "false")
            @RequestParam(required = false) Boolean revisado,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return adminMapeoServicio.listar(revisado, paginacion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Fijar el alimento equivalente de un ingrediente",
            description = "El emparejamiento queda MANUAL y revisado (alimentoId nulo: sin equivalente). Recalcula las "
                    + "recetas de TheMealDB que usan ese ingrediente y devuelve cuántas.")
    @ApiResponse(responseCode = "200", description = "Emparejamiento actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public MapeoIngredienteResponse actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarMapeoRequest peticion) {
        return adminMapeoServicio.actualizar(id, peticion);
    }
}
