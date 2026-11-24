package com.nutrisport.auth.web;

import com.nutrisport.comun.config.JwtProperties;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie del refresh token (§5.5): {@code HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth}.
 * El JavaScript del frontend no puede leerla y el navegador solo la envía a las rutas de auth.
 */
@Component
public class CookieRefresh {

    public static final String NOMBRE = "ns_refresh";
    static final String RUTA = "/api/v1/auth";

    private final boolean segura;
    private final Duration duracionPersistente;

    public CookieRefresh(@Value("${app.cookie.secure}") boolean segura, JwtProperties jwtProperties) {
        this.segura = segura;
        this.duracionPersistente = Duration.ofDays(jwtProperties.refreshDias());
    }

    /**
     * @param persistente {@code true} con "Mantener la sesión iniciada": dura {@code refreshDias};
     *                    {@code false}: cookie de sesión, se borra al cerrar el navegador
     */
    public ResponseCookie crear(String token, boolean persistente) {
        ResponseCookie.ResponseCookieBuilder cookie = base(token);
        if (persistente) {
            cookie.maxAge(duracionPersistente);
        }
        return cookie.build();
    }

    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path(RUTA);
    }
}
