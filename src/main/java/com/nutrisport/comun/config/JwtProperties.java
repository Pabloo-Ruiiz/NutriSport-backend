package com.nutrisport.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los tokens JWT, leída de las propiedades con prefijo {@code app.jwt}.
 *
 * @param secret         secreto para firmar el access token con HS256 (≥ 256 bits); llega por variable de entorno
 * @param accessMinutos  minutos de validez del access token
 * @param refreshDias    días de validez del refresh token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int accessMinutos, int refreshDias) {
}
