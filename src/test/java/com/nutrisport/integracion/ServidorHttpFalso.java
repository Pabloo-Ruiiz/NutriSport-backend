package com.nutrisport.integracion;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP local (el de {@code com.sun.net.httpserver} del JDK) que hace de proveedor externo en los
 * tests: los tests nunca llaman a las APIs reales. Devuelve las respuestas encoladas en orden y anota cada
 * petición recibida (ruta, parámetros y cabeceras) para comprobarla después.
 * <p>
 * Al contrario que un mock del cliente, la petición sale de verdad por la red: así se prueban los timeouts,
 * el User-Agent y la autenticación básica tal como los envía el cliente HTTP.
 */
public final class ServidorHttpFalso implements AutoCloseable {

    /** Respuesta encolada. {@code retraso} simula un proveedor lento. */
    public record Respuesta(int estado, Map<String, String> cabeceras, String cuerpo, Duration retraso) {

        public static Respuesta json(String cuerpo) {
            return new Respuesta(200, Map.of("Content-Type", "application/json"), cuerpo, Duration.ZERO);
        }

        public static Respuesta estado(int estado) {
            return new Respuesta(estado, Map.of(), "", Duration.ZERO);
        }

        public static Respuesta estado(int estado, Map<String, String> cabeceras) {
            return new Respuesta(estado, cabeceras, "", Duration.ZERO);
        }

        public Respuesta conRetraso(Duration nuevoRetraso) {
            return new Respuesta(estado, cabeceras, cuerpo, nuevoRetraso);
        }
    }

    /** Petición recibida, con la URI ya decodificada. */
    public record Peticion(String metodo, URI uri, Headers cabeceras) {

        public String ruta() {
            return uri.getPath();
        }

        /** Valor decodificado de un parámetro de la query, o nulo. */
        public String parametro(String nombre) {
            String query = uri.getQuery();
            if (query == null) {
                return null;
            }
            for (String par : query.split("&")) {
                int igual = par.indexOf('=');
                if (igual > 0 && par.substring(0, igual).equals(nombre)) {
                    return par.substring(igual + 1);
                }
            }
            return null;
        }

        public String cabecera(String nombre) {
            return cabeceras.getFirst(nombre);
        }
    }

    private final HttpServer servidor;
    private final ConcurrentLinkedQueue<Respuesta> pendientes = new ConcurrentLinkedQueue<>();
    private final List<Peticion> recibidas = Collections.synchronizedList(new ArrayList<>());

    public ServidorHttpFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    /** URL base del servidor, p. ej. {@code http://127.0.0.1:54321}. */
    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Encola respuestas; si se agotan, el servidor responde 500. */
    public void encolar(Respuesta... respuestas) {
        pendientes.addAll(List.of(respuestas));
    }

    public List<Peticion> recibidas() {
        synchronized (recibidas) {
            return List.copyOf(recibidas);
        }
    }

    /** Olvida las respuestas pendientes y las peticiones recibidas. */
    public void reiniciar() {
        pendientes.clear();
        recibidas.clear();
    }

    /** Contenido de un fichero de {@code src/test/resources}. */
    public static String recurso(String ruta) {
        try (InputStream entrada = ServidorHttpFalso.class.getResourceAsStream("/" + ruta)) {
            if (entrada == null) {
                throw new IllegalArgumentException("No existe el recurso de test " + ruta);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        recibidas.add(new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI(),
                intercambio.getRequestHeaders()));
        Respuesta respuesta = pendientes.poll();
        if (respuesta == null) {
            respuesta = Respuesta.estado(500);
        }
        try {
            if (!respuesta.retraso().isZero()) {
                Thread.sleep(respuesta.retraso());
            }
            respuesta.cabeceras().forEach((nombre, valor) -> intercambio.getResponseHeaders().add(nombre, valor));
            byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(respuesta.estado(), cuerpo.length == 0 ? -1 : cuerpo.length);
            if (cuerpo.length > 0) {
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // El cliente ya cortó la conexión (timeout): no hay a quién responder
        } finally {
            intercambio.close();
        }
    }
}
