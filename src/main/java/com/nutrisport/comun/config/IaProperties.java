package com.nutrisport.comun.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la inteligencia artificial (§5.7), con prefijo {@code app.ia}.
 *
 * @param modo                 SIMULADO (por defecto, sin llamadas externas) o REAL (proveedor de Spring AI)
 * @param proveedor            nombre del proveedor que se guarda en uso_ia en modo real
 * @param limiteAnalisisDiario análisis de imagen y de etiquetas por usuario y día (comparten límite)
 * @param limiteMensajesDiario mensajes al asistente por usuario y día
 * @param costeEntradaMillon   precio en USD por millón de tokens de entrada, para estimar el gasto
 * @param costeSalidaMillon    precio en USD por millón de tokens de salida
 * @param timeoutSegundos      tiempo máximo de una llamada al proveedor
 */
@Validated
@ConfigurationProperties("app.ia")
public record IaProperties(
        @NotNull Modo modo,
        @NotBlank String proveedor,
        @Min(0) int limiteAnalisisDiario,
        @Min(0) int limiteMensajesDiario,
        @NotNull @DecimalMin("0") BigDecimal costeEntradaMillon,
        @NotNull @DecimalMin("0") BigDecimal costeSalidaMillon,
        @Min(1) int timeoutSegundos) {

    /** Modo de funcionamiento; en las propiedades se escribe en minúsculas ({@code simulado} / {@code real}). */
    public enum Modo {
        SIMULADO,
        REAL
    }
}
