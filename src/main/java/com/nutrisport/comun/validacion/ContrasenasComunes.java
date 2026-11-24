package com.nutrisport.comun.validacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Lista de contraseñas comunes de {@code contrasenas-comunes.txt}, cargada una sola vez al arrancar.
 * Si el fichero no está, la aplicación no arranca: es preferible a aceptar contraseñas sin comprobar.
 */
@Component
public class ContrasenasComunes {

    static final String FICHERO = "contrasenas-comunes.txt";

    private final Set<String> contrasenas;

    public ContrasenasComunes() {
        ClassPathResource recurso = new ClassPathResource(FICHERO);
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            this.contrasenas = lector.lines()
                    .map(String::trim)
                    .filter(linea -> !linea.isEmpty())
                    .map(linea -> linea.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("No se puede leer " + FICHERO, e);
        }
        if (contrasenas.isEmpty()) {
            throw new IllegalStateException(FICHERO + " está vacío");
        }
    }

    /** Compara en minúsculas. */
    public boolean contiene(String contrasena) {
        return contrasena != null && contrasenas.contains(contrasena.toLowerCase(Locale.ROOT));
    }

    public int tamano() {
        return contrasenas.size();
    }
}
