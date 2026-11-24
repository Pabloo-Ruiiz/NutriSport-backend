package com.nutrisport.almacenamiento;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del almacenamiento de fotos en MinIO o S3 (§5.8), con prefijo {@code app.almacenamiento}.
 *
 * @param endpoint        URL con la que el backend llega a MinIO (en Docker, {@code http://minio:9000})
 * @param endpointPublico URL que ve el navegador ({@code http://localhost:9000}); con ella se firman las
 *                        URLs prefirmadas, porque la firma incluye el host
 * @param region          región S3 (MinIO la ignora, pero el SDK la exige)
 * @param bucket          bucket de las fotos
 * @param accessKey       usuario; llega por variable de entorno
 * @param secretKey       clave; llega por variable de entorno
 * @param urlMinutos      minutos de validez de una URL prefirmada
 * @param retencionDias   días que se conservan las fotos antes de borrarlas
 */
@Validated
@ConfigurationProperties("app.almacenamiento")
public record AlmacenamientoProperties(
        @NotNull URI endpoint,
        @NotNull URI endpointPublico,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @Min(1) int urlMinutos,
        @Min(1) int retencionDias) {

    /** Evita que la clave aparezca en logs o mensajes de error. */
    @Override
    public String toString() {
        return "AlmacenamientoProperties[endpoint=" + endpoint + ", endpointPublico=" + endpointPublico
                + ", region=" + region + ", bucket=" + bucket + ", urlMinutos=" + urlMinutos
                + ", retencionDias=" + retencionDias + "]";
    }
}
