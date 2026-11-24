package com.nutrisport.auth.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Respuesta cuando un usuario autenticado no tiene el rol necesario (p. ej. rutas {@code /admin}):
 * 403 {@code SIN_PERMISO} en formato Problem Details.
 */
@Component
public class ManejadorAccesoDenegado implements AccessDeniedHandler {

    private final EscritorProblemas escritor;

    ManejadorAccesoDenegado(EscritorProblemas escritor) {
        this.escritor = escritor;
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta,
            AccessDeniedException excepcion) throws IOException {
        escritor.escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "SIN_PERMISO",
                "No tienes permiso para realizar esta operación");
    }
}
