package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.ActualizarUsuarioAdminRequest;
import com.nutrisport.admin.dto.UsuarioAdminResponse;
import com.nutrisport.admin.servicio.AdminUsuarioServicio;
import com.nutrisport.auth.entidad.EstadoUsuario;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de usuarios por un administrador (§7.10). Solo datos de cuenta, nunca datos de salud. */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Solo para administradores: catálogo de alimentos (alta, edición, revisión de "
        + "alérgenos e importación CSV), categorías, recetas públicas y su publicación, emparejamientos de "
        + "ingredientes y usuarios (búsqueda, bloqueo y roles). Cada acción queda auditada. Nunca da acceso a datos "
        + "de salud.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AdminUsuarioController {

    private final AdminUsuarioServicio adminUsuarioServicio;

    public AdminUsuarioController(AdminUsuarioServicio adminUsuarioServicio) {
        this.adminUsuarioServicio = adminUsuarioServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar usuarios",
            description = "Los más recientes primero. Máximo 100 por página.")
    @ApiResponse(responseCode = "200", description = "Página de usuarios")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<UsuarioAdminResponse> listar(
            @Parameter(description = "Texto contenido en el nombre o el email", example = "alex")
            @RequestParam(required = false) String q,
            @Parameter(description = "Solo usuarios en este estado")
            @RequestParam(required = false) EstadoUsuario estado,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return adminUsuarioServicio.listar(q, estado, paginacion);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Datos de cuenta de un usuario",
            description = "Nombre, email, roles, estado, verificación del email, último acceso y alta.")
    @ApiResponse(responseCode = "200", description = "El usuario")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public UsuarioAdminResponse obtener(@PathVariable UUID id) {
        return adminUsuarioServicio.obtener(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Bloquear o desbloquear y cambiar roles",
            description = "Los campos nulos no cambian. El estado solo puede ser ACTIVO o BLOQUEADO; bloquear cierra "
                    + "todas sus sesiones. Una cuenta eliminada no se puede modificar, y un administrador no puede "
                    + "bloquearse ni quitarse el rol ADMIN (422).")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public UsuarioAdminResponse actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioAdminRequest peticion) {
        return adminUsuarioServicio.actualizar(id, peticion);
    }
}
