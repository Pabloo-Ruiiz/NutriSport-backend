package com.nutrisport.auth.seguridad;

import com.nutrisport.comun.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Genera y valida los access tokens JWT (§5.5): HS256 con un secreto de al menos 256 bits,
 * claims {@code sub} (id del usuario), {@code roles}, {@code jti}, {@code iat} y {@code exp}.
 */
@Service
public class JwtServicio {

    /** HS256 exige una clave de al menos 256 bits. */
    private static final int BYTES_MINIMOS_SECRETO = 32;
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey clave;
    private final JwtParser parser;
    private final long segundosValidez;

    public JwtServicio(JwtProperties propiedades) {
        String secreto = propiedades.secret();
        // El secreto se usa tal cual (bytes UTF-8): no se escribe nunca en el mensaje de error
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS_SECRETO) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos " + BYTES_MINIMOS_SECRETO + " bytes (variable JWT_SECRET)");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(clave).build();
        this.segundosValidez = propiedades.accessMinutos() * 60L;
    }

    public String generarAccessToken(UsuarioAutenticado usuario) {
        Instant ahora = Instant.now();
        List<String> roles = usuario.getRoles().stream().map(Enum::name).sorted().toList();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim(CLAIM_ROLES, roles)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(segundosValidez)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Devuelve los claims si el token está firmado con nuestra clave, bien formado y sin caducar;
     * en cualquier otro caso devuelve vacío sin lanzar excepciones.
     */
    public Optional<Claims> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Validez del access token en segundos (para {@code expiresIn} en la respuesta de login). */
    public long segundosValidez() {
        return segundosValidez;
    }
}
