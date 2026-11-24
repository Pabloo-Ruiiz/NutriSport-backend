package com.nutrisport.auth.seguridad;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acceso al usuario autenticado. Los servicios filtran por {@link #usuarioActualId()} todas las
 * consultas de recursos personales (§8.2); el id nunca se toma de la petición.
 */
@Component
public class SeguridadUtils {

    public UsuarioAutenticado usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new AuthenticationCredentialsNotFoundException("No hay ningún usuario autenticado");
    }

    public UUID usuarioActualId() {
        return usuarioActual().getId();
    }
}
