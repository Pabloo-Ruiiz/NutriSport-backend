package com.nutrisport.auth.servicio;

import com.nutrisport.auth.dto.TokenResponse;

/**
 * Resultado interno del login: la respuesta JSON y el refresh token en claro, que el controlador
 * pone en la cookie. No se devuelve nunca tal cual en la API.
 */
public record ResultadoLogin(TokenResponse respuesta, String refreshToken, boolean mantenerSesion) {
}
