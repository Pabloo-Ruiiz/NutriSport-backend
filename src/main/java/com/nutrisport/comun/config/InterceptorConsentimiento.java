package com.nutrisport.comun.config;

import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.comun.excepcion.AccesoDenegadoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bloquea las funciones que tratan datos de salud si el usuario ha revocado el consentimiento
 * (§3.A y §8.3): {@code 403 CONSENTIMIENTO_REVOCADO}. Las rutas se registran en {@link WebConfig}.
 * <p>
 * El estado del consentimiento viene del {@link UsuarioAutenticado} que el filtro JWT carga de la base
 * de datos en cada petición, así que la revocación surte efecto en la petición siguiente.
 */
@Component
public class InterceptorConsentimiento implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        // Sin usuario autenticado no hay nada que comprobar: la seguridad ya responde 401
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                && !usuario.isConsentimientoVigente()) {
            throw new AccesoDenegadoException("CONSENTIMIENTO_REVOCADO",
                    "Has revocado el consentimiento para tratar tus datos de salud. "
                            + "Vuelve a darlo en tu cuenta para usar esta función");
        }
        return true;
    }
}
