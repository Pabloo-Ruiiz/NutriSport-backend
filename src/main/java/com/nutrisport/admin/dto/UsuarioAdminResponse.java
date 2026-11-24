package com.nutrisport.admin.dto;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de cuenta de un usuario para la administración (§2.2). NUNCA incluye datos de salud: ni perfil,
 * ni comidas, ni fotos, ni el hash de la contraseña.
 */
public record UsuarioAdminResponse(
        UUID id,
        String nombre,
        String email,
        Set<Rol> roles,
        EstadoUsuario estado,
        boolean emailVerificado,
        OffsetDateTime ultimoAcceso,
        OffsetDateTime creadoEn) {
}
