package com.nutrisport.auth.seguridad;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada petición con la cabecera {@code Authorization: Bearer <jwt>} (§5.5).
 * <p>
 * Carga el usuario de la base de datos en cada petición, así que un bloqueo o una eliminación
 * surte efecto al momento aunque el token siga vigente. Si el token falta o no es válido no hace
 * nada: la petición sigue sin autenticar y el {@link PuntoEntradaNoAutenticado} responde 401
 * en las rutas protegidas.
 * <p>
 * No es un {@code @Component} a propósito: Spring Boot registraría cualquier filtro que sea un bean
 * también en la cadena de servlets y se ejecutaría dos veces. Lo crea y lo añade la configuración
 * de seguridad.
 */
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtServicio jwtServicio;
    private final UsuarioDetailsServiceImpl usuarioDetailsService;
    private final WebAuthenticationDetailsSource fuenteDetalles = new WebAuthenticationDetailsSource();

    public FiltroJwt(JwtServicio jwtServicio, UsuarioDetailsServiceImpl usuarioDetailsService) {
        this.jwtServicio = jwtServicio;
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extraerToken(peticion)
                    .flatMap(jwtServicio::validar)
                    .flatMap(this::cargarUsuario)
                    .filter(UsuarioAutenticado::isEnabled)
                    .ifPresent(usuario -> autenticar(usuario, peticion));
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static Optional<String> extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            return Optional.empty();
        }
        String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private Optional<UsuarioAutenticado> cargarUsuario(Claims claims) {
        try {
            return Optional.of(usuarioDetailsService.cargarPorId(UUID.fromString(claims.getSubject())));
        } catch (IllegalArgumentException | NullPointerException | UsernameNotFoundException e) {
            // sub ausente, con formato incorrecto o de un usuario que ya no existe
            return Optional.empty();
        }
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest peticion) {
        // Sin credenciales: la autenticación ya la ha hecho el JWT
        UsernamePasswordAuthenticationToken autenticacion =
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
        autenticacion.setDetails(fuenteDetalles.buildDetails(peticion));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
    }
}
