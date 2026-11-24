package com.nutrisport.ia.puerto;

import java.util.Map;

/**
 * Generación de texto con IA (§5.7). Tiene un adaptador real (Spring AI) y uno simulado; los módulos
 * de dominio solo dependen de esta interfaz. A la IA nunca se le envían nombre, email ni ids (§4.1).
 */
public interface GeneracionTextoPuerto {

    /**
     * @param plantilla nombre de una plantilla de {@code src/main/resources/prompts/} sin la extensión
     *                  {@code .st} (formato StringTemplate)
     * @param variables valores de las variables de la plantilla
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException si el proveedor no responde
     */
    ResultadoIa<String> generar(String plantilla, Map<String, Object> variables);
}
