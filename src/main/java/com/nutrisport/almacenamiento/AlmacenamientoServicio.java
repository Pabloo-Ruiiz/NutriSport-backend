package com.nutrisport.almacenamiento;

import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Acceso al bucket privado de fotos (§5.8 y §8.4). Solo el backend escribe; el navegador lee con URLs
 * prefirmadas GET de corta duración. Los fallos de MinIO o S3 se traducen en 503 SERVICIO_NO_DISPONIBLE.
 */
@Service
public class AlmacenamientoServicio {

    /** Máximo de claves por petición DeleteObjects en S3. */
    static final int TAMANO_LOTE_BORRADO = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenamientoServicio.class);
    private static final String NO_DISPONIBLE = "El almacenamiento de fotos no está disponible. Inténtalo más tarde";

    private final S3Client s3;
    private final S3Presigner firmante;
    private final AlmacenamientoProperties propiedades;

    public AlmacenamientoServicio(S3Client s3, S3Presigner firmante, AlmacenamientoProperties propiedades) {
        this.s3 = s3;
        this.firmante = firmante;
        this.propiedades = propiedades;
    }

    public void guardar(String clave, byte[] contenido, String contentType) {
        try {
            s3.putObject(peticion -> peticion.bucket(propiedades.bucket()).key(clave).contentType(contentType)
                    .contentLength((long) contenido.length), RequestBody.fromBytes(contenido));
        } catch (SdkException e) {
            throw noDisponible("guardar " + clave, e);
        }
    }

    /** Contenido de un objeto (p. ej. la foto que se envía a la IA). */
    public byte[] descargar(String clave) {
        try {
            return s3.getObjectAsBytes(peticion -> peticion.bucket(propiedades.bucket()).key(clave)).asByteArray();
        } catch (SdkException e) {
            throw noDisponible("descargar " + clave, e);
        }
    }

    /** URL prefirmada GET válida {@code url-minutos} minutos, firmada con el endpoint público. */
    public String urlLectura(String clave) {
        return firmante.presignGetObject(peticion -> peticion
                        .signatureDuration(Duration.ofMinutes(propiedades.urlMinutos()))
                        .getObjectRequest(objeto -> objeto.bucket(propiedades.bucket()).key(clave)))
                .url()
                .toString();
    }

    /** Borra un objeto; si no existe, no hace nada. */
    public void eliminar(String clave) {
        try {
            s3.deleteObject(peticion -> peticion.bucket(propiedades.bucket()).key(clave));
        } catch (SdkException e) {
            throw noDisponible("eliminar " + clave, e);
        }
    }

    /**
     * Borra todos los objetos cuya clave empieza por el prefijo, en lotes de 1000.
     *
     * @param prefijo prefijo no vacío y terminado en "/" (p. ej. el de un usuario), para no borrar el
     *                bucket entero por error
     * @return número de objetos borrados
     */
    public int eliminarPrefijo(String prefijo) {
        if (prefijo == null || prefijo.isBlank() || !prefijo.endsWith("/")) {
            throw new IllegalArgumentException("El prefijo debe ser no vacío y terminar en '/': " + prefijo);
        }
        try {
            int borrados = 0;
            List<ObjectIdentifier> lote = new ArrayList<>();
            for (S3Object objeto : s3.listObjectsV2Paginator(peticion -> peticion.bucket(propiedades.bucket())
                    .prefix(prefijo)).contents()) {
                lote.add(ObjectIdentifier.builder().key(objeto.key()).build());
                if (lote.size() == TAMANO_LOTE_BORRADO) {
                    borrados += borrarLote(lote);
                    lote.clear();
                }
            }
            if (!lote.isEmpty()) {
                borrados += borrarLote(lote);
            }
            return borrados;
        } catch (SdkException e) {
            throw noDisponible("eliminar el prefijo " + prefijo, e);
        }
    }

    /** Borra un lote; en modo silencioso S3 solo informa de las claves que no ha podido borrar. */
    private int borrarLote(List<ObjectIdentifier> claves) {
        DeleteObjectsResponse respuesta = s3.deleteObjects(peticion -> peticion.bucket(propiedades.bucket())
                .delete(Delete.builder().objects(claves).quiet(true).build()));
        respuesta.errors().forEach(error ->
                LOG.error("No se ha podido borrar {}: {} {}", error.key(), error.code(), error.message()));
        return claves.size() - respuesta.errors().size();
    }

    private static ServicioNoDisponibleException noDisponible(String operacion, SdkException causa) {
        LOG.error("Fallo del almacenamiento al {}: {}", operacion, causa.getMessage());
        return new ServicioNoDisponibleException(NO_DISPONIBLE);
    }
}
