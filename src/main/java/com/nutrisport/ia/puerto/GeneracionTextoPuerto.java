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

    /**
     * Como {@link #generar} pero con salida estructurada: el proveedor recibe el esquema JSON de {@code tipo} y la
     * respuesta se convierte a ese tipo. El resultado es una propuesta: quien llama la valida antes de usarla.
     *
     * @param tipo clase (normalmente un record) en la que se convierte la respuesta
     * @return resultado con el objeto convertido; su contenido puede ser nulo si el modelo no propone nada
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException si el proveedor no responde
     */
    <T> ResultadoIa<T> generarEstructurado(String plantilla, Map<String, Object> variables, Class<T> tipo);
}
