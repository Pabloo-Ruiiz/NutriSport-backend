package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.ActualizarUsuarioAdminRequest;
import com.nutrisport.admin.dto.UsuarioAdminResponse;
import com.nutrisport.admin.servicio.AdminUsuarioServicio;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Administración: usuarios", description = "Búsqueda, bloqueo y roles")
public class AdminUsuarioController {

    private final AdminUsuarioServicio adminUsuarioServicio;

    public AdminUsuarioController(AdminUsuarioServicio adminUsuarioServicio) {
        this.adminUsuarioServicio = adminUsuarioServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar usuarios", description = "q en el nombre o el email; estado opcional. Máximo 100 por página.")
    public PaginaResponse<UsuarioAdminResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoUsuario estado,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return adminUsuarioServicio.listar(q, estado, paginacion);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Datos de cuenta de un usuario")
    public UsuarioAdminResponse obtener(@PathVariable UUID id) {
        return adminUsuarioServicio.obtener(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Bloquear o desbloquear y cambiar roles",
            description = "Los campos nulos no cambian. Bloquear cierra sus sesiones. No puedes bloquearte ni "
                    + "quitarte el rol ADMIN (422).")
    public UsuarioAdminResponse actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioAdminRequest peticion) {
        return adminUsuarioServicio.actualizar(id, peticion);
    }
}
