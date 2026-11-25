package com.nutrisport.auth.dto;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Unidades;
import java.util.Set;
import java.util.UUID;

/** Datos de la cuenta que ve el propio usuario. Nunca incluye el hash de la contraseña. */
public record CuentaResponse(
        UUID id,
        String nombre,
        String email,
        Set<Rol> roles,
        EstadoUsuario estado,
        boolean emailVerificado,
        boolean consentimientoSaludVigente,
        Unidades unidades,
        boolean insightsActivos) {
}
