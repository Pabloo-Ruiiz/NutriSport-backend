package com.nutrisport.auth.servicio;

import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.TokenUsuario;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.TokenUsuarioRepositorio;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.util.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokens de un solo uso enviados por email (verificación, recuperación, cambio de email).
 * Solo se guarda el SHA-256: el token en claro solo existe en el enlace del correo.
 */
@Service
@Transactional
public class TokenUsuarioServicio {

    static final String TOKEN_NO_VALIDO = "TOKEN_NO_VALIDO";
    private static final String MENSAJE_NO_VALIDO = "El enlace no es válido, ya se ha usado o ha caducado";

    private final TokenUsuarioRepositorio tokenUsuarioRepositorio;

    public TokenUsuarioServicio(TokenUsuarioRepositorio tokenUsuarioRepositorio) {
        this.tokenUsuarioRepositorio = tokenUsuarioRepositorio;
    }

    /**
     * Invalida los tokens pendientes del mismo tipo (solo vale el último enlace enviado), guarda el
     * hash del nuevo y devuelve el token en claro para el correo.
     * <p>
     * La invalidación vacía el contexto de persistencia: haz los cambios del usuario antes de llamar.
     */
    public String crear(Usuario usuario, TipoToken tipo, Duration validez, String datoExtra) {
        tokenUsuarioRepositorio.invalidarPendientes(usuario.getId(), tipo);

        String token = Tokens.generar();
        TokenUsuario tokenUsuario = new TokenUsuario();
        tokenUsuario.setUsuario(usuario);
        tokenUsuario.setTipo(tipo);
        tokenUsuario.setTokenHash(Tokens.sha256Hex(token));
        tokenUsuario.setDatoExtra(datoExtra);
        tokenUsuario.setExpiraEn(OffsetDateTime.now(ZoneOffset.UTC).plus(validez));
        tokenUsuarioRepositorio.save(tokenUsuario);
        return token;
    }

    /**
     * Marca el token como usado y lo devuelve. Si no existe, no es de ninguno de los tipos admitidos,
     * ya se usó o ha caducado, lanza {@code 400 TOKEN_NO_VALIDO} con el mismo mensaje en todos los casos.
     *
     * @param tipos tipos admitidos (p. ej. verificación y cambio de email en {@code /verificar-email})
     */
    public TokenUsuario consumir(String token, TipoToken... tipos) {
        if (token == null || token.isBlank()) {
            throw new PeticionInvalidaException(TOKEN_NO_VALIDO, MENSAJE_NO_VALIDO);
        }
        Set<TipoToken> admitidos = Set.of(tipos);
        TokenUsuario tokenUsuario = tokenUsuarioRepositorio
                .findByTokenHash(Tokens.sha256Hex(token.trim()))
                .filter(encontrado -> admitidos.contains(encontrado.getTipo()))
                .filter(encontrado -> encontrado.estaVigente(Instant.now()))
                .orElseThrow(() -> new PeticionInvalidaException(TOKEN_NO_VALIDO, MENSAJE_NO_VALIDO));
        tokenUsuario.setUsadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        return tokenUsuario;
    }
}
