package com.nutrisport.auth.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Respuesta para las rutas protegidas sin un JWT válido: 401 {@code NO_AUTENTICADO} en formato
 * Problem Details. No indica el motivo concreto (falta, caducado, firma) para no dar pistas.
 */
@Component
public class PuntoEntradaNoAutenticado implements AuthenticationEntryPoint {

    private final EscritorProblemas escritor;

    PuntoEntradaNoAutenticado(EscritorProblemas escritor) {
        this.escritor = escritor;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta,
            AuthenticationException excepcion) throws IOException {
        escritor.escribir(peticion, respuesta, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Es necesario iniciar sesión");
    }
}
