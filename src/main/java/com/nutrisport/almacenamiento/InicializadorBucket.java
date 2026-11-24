package com.nutrisport.almacenamiento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Al arrancar, crea el bucket de fotos si no existe (§5.8). Nunca le asigna una política pública: el
 * bucket es privado y solo se lee con URLs prefirmadas.
 * <p>
 * Si MinIO no responde, lo registra y deja arrancar la aplicación: el resto de funciones no dependen de
 * él, y las subidas de fotos responderán 503 hasta que vuelva. Se desactiva con
 * {@code app.almacenamiento.inicializar-bucket=false} (los tests que no usan MinIO).
 */
@Component
@ConditionalOnProperty(name = "app.almacenamiento.inicializar-bucket", havingValue = "true", matchIfMissing = true)
public class InicializadorBucket implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorBucket.class);

    private final S3Client s3;
    private final AlmacenamientoProperties propiedades;

    public InicializadorBucket(S3Client s3, AlmacenamientoProperties propiedades) {
        this.s3 = s3;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        String bucket = propiedades.bucket();
        try {
            s3.headBucket(peticion -> peticion.bucket(bucket));
            LOG.debug("El bucket {} ya existe", bucket);
        } catch (NoSuchBucketException e) {
            s3.createBucket(peticion -> peticion.bucket(bucket));
            LOG.info("Bucket {} creado (privado)", bucket);
        } catch (SdkException e) {
            LOG.warn("No se ha podido comprobar el bucket {} en {}: {}", bucket, propiedades.endpoint(), e.getMessage());
        }
    }
}
