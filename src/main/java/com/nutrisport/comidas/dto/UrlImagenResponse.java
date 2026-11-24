package com.nutrisport.comidas.dto;

import java.time.OffsetDateTime;

/** URL prefirmada de lectura de la foto de un análisis (§5.8) y cuándo caduca. */
public record UrlImagenResponse(String url, OffsetDateTime expiraEn) {
}
