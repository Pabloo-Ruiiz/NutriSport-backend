package com.nutrisport.admin.dto;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Cambio de estado o de roles de un usuario (PATCH /api/v1/admin/usuarios/{id}). Los campos nulos no se
 * modifican. El estado solo puede ser ACTIVO o BLOQUEADO (la eliminación la pide el propio usuario).
 */
public record ActualizarUsuarioAdminRequest(
        EstadoUsuario estado,

        @Size(min = 1, message = "El usuario debe tener al menos un rol")
        Set<Rol> roles) {
}
