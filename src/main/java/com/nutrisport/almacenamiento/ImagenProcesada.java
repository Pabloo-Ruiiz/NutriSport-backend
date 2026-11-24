package com.nutrisport.almacenamiento;

/**
 * Resultado de procesar una foto: JPEG recodificado (sin EXIF) y su miniatura.
 *
 * @param imagen    JPEG con el lado largo ≤ 1024 px
 * @param miniatura JPEG con el lado largo ≤ 320 px
 * @param ancho     ancho de {@code imagen} en píxeles
 * @param alto      alto de {@code imagen} en píxeles
 */
public record ImagenProcesada(byte[] imagen, byte[] miniatura, int ancho, int alto) {
}
