package com.nutrisport.auth.dto;

/**
 * Respuesta del login: access token JWT (el refresh token va solo en la cookie HttpOnly).
 *
 * @param expiraEn segundos de validez del access token
 */
public record TokenResponse(String accessToken, String tipo, long expiraEn, CuentaResponse cuenta) {

    public static final String TIPO_BEARER = "Bearer";

    /** Respuesta con {@code tipo = "Bearer"}. */
    public TokenResponse(String accessToken, long expiraEn, CuentaResponse cuenta) {
        this(accessToken, TIPO_BEARER, expiraEn, cuenta);
    }
}
