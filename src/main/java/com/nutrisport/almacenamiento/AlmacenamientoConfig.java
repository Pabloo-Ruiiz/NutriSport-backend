package com.nutrisport.almacenamiento;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes del AWS SDK v2 para MinIO (§5.8). Usan acceso por ruta ({@code endpoint/bucket/clave}),
 * porque MinIO no resuelve buckets como subdominios. El cliente habla con {@code endpoint}; el firmante
 * de URLs usa {@code endpointPublico}, que es el host que verá el navegador.
 */
@Configuration
public class AlmacenamientoConfig {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(AlmacenamientoProperties propiedades) {
        return S3Client.builder()
                .endpointOverride(propiedades.endpoint())
                .region(Region.of(propiedades.region()))
                .credentialsProvider(credenciales(propiedades))
                .serviceConfiguration(rutaEnLugarDeSubdominio())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(AlmacenamientoProperties propiedades) {
        return S3Presigner.builder()
                .endpointOverride(propiedades.endpointPublico())
                .region(Region.of(propiedades.region()))
                .credentialsProvider(credenciales(propiedades))
                .serviceConfiguration(rutaEnLugarDeSubdominio())
                .build();
    }

    private static StaticCredentialsProvider credenciales(AlmacenamientoProperties propiedades) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(propiedades.accessKey(), propiedades.secretKey()));
    }

    private static S3Configuration rutaEnLugarDeSubdominio() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
