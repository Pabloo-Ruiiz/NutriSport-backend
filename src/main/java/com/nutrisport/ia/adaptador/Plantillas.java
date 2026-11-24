package com.nutrisport.ia.adaptador;

import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** Plantillas de prompts de {@code src/main/resources/prompts/} (formato StringTemplate, {@code .st}). */
final class Plantillas {

    /** Solo nombres simples: evita leer ficheros fuera de prompts/ con "../". */
    private static final Pattern NOMBRE = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    private Plantillas() {
    }

    static Resource cargar(String plantilla) {
        if (plantilla == null || !NOMBRE.matcher(plantilla).matches()) {
            throw new IllegalArgumentException("Nombre de plantilla no válido: " + plantilla);
        }
        Resource recurso = new ClassPathResource("prompts/" + plantilla + ".st");
        if (!recurso.exists()) {
            throw new IllegalArgumentException("No existe la plantilla prompts/" + plantilla + ".st");
        }
        return recurso;
    }
}
