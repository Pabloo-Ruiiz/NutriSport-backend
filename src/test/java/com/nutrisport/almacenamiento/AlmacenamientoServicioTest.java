package com.nutrisport.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Guardado en MinIO, lectura con URL prefirmada y borrado (§5.8). */
class AlmacenamientoServicioTest extends IntegracionBase {

    private final AlmacenamientoServicio almacenamiento;
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    AlmacenamientoServicioTest(AlmacenamientoServicio almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    @Test
    void loGuardadoSeDescargaConLaUrlPrefirmadaYDejaDeEstarAlBorrarlo() throws Exception {
        String clave = ClavesAlmacenamiento.claveComida(UUID.randomUUID(), LocalDate.of(2026, 11, 24));
        byte[] contenido = "bytes de una foto de prueba".getBytes(StandardCharsets.UTF_8);

        almacenamiento.guardar(clave, contenido, "image/jpeg");

        HttpResponse<byte[]> descarga = descargar(almacenamiento.urlLectura(clave));
        assertThat(descarga.statusCode()).isEqualTo(200);
        assertThat(descarga.body()).isEqualTo(contenido);
        assertThat(descarga.headers().firstValue("Content-Type")).hasValue("image/jpeg");

        almacenamiento.eliminar(clave);

        // MinIO responde 404 (NoSuchKey); S3 sin permiso de listado respondería 403
        assertThat(descargar(almacenamiento.urlLectura(clave)).statusCode()).isIn(403, 404);
    }

    private HttpResponse<byte[]> descargar(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
