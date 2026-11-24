package com.nutrisport.integracion.comun;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de las APIs externas (prefijo {@code app.integracion}, docs/API_INTEGRATIONS.md §8). Los
 * valores llegan por variables de entorno ({@code OFF_*}, {@code THEMEALDB_*}, {@code USDA_*}); en los tests,
 * URLs falsas que no se pueden resolver, porque los tests nunca llaman a los proveedores reales.
 *
 * @param off       Open Food Facts
 * @param themealdb TheMealDB
 * @param usda      USDA FoodData Central
 */
@ConfigurationProperties(prefix = "app.integracion")
public record IntegracionProperties(Proveedor off, Proveedor themealdb, Proveedor usda) {

    public IntegracionProperties {
        off = off != null ? off : Proveedor.VACIO;
        themealdb = themealdb != null ? themealdb : Proveedor.VACIO;
        usda = usda != null ? usda : Proveedor.VACIO;
    }

    /** Configuración de un proveedor concreto. */
    public Proveedor de(ProveedorExterno proveedor) {
        return switch (proveedor) {
            case OPEN_FOOD_FACTS -> off;
            case THEMEALDB -> themealdb;
            case USDA -> usda;
        };
    }

    /**
     * Configuración de un proveedor. Los campos que no usa un proveedor quedan nulos.
     *
     * @param baseUrl           URL base de la API (Open Food Facts: staging {@code world.openfoodfacts.net} en
     *                          desarrollo)
     * @param searchUrl         URL de la búsqueda por texto (solo Open Food Facts: Search-a-licious)
     * @param userAgent         User-Agent obligatorio con un correo de contacto (Open Food Facts)
     * @param usuarioStaging    usuario de la autenticación básica de staging (Open Food Facts: {@code off})
     * @param claveStaging      contraseña de la autenticación básica de staging (Open Food Facts: {@code off})
     * @param apiKey            clave de la API (TheMealDB y USDA)
     * @param timeoutConexionMs tiempo máximo para conectar; 2000 ms si no se indica (§6.3)
     * @param timeoutLecturaMs  tiempo máximo de espera de la respuesta; 5000 ms si no se indica (§6.3)
     */
    public record Proveedor(String baseUrl, String searchUrl, String userAgent, String usuarioStaging,
            String claveStaging, String apiKey, Integer timeoutConexionMs, Integer timeoutLecturaMs) {

        static final int TIMEOUT_CONEXION_POR_DEFECTO_MS = 2000;
        static final int TIMEOUT_LECTURA_POR_DEFECTO_MS = 5000;

        static final Proveedor VACIO = new Proveedor(null, null, null, null, null, null, null, null);

        public Proveedor {
            baseUrl = vacioANulo(baseUrl);
            searchUrl = vacioANulo(searchUrl);
            userAgent = vacioANulo(userAgent);
            usuarioStaging = vacioANulo(usuarioStaging);
            claveStaging = vacioANulo(claveStaging);
            apiKey = vacioANulo(apiKey);
            timeoutConexionMs = timeoutConexionMs != null ? timeoutConexionMs : TIMEOUT_CONEXION_POR_DEFECTO_MS;
            timeoutLecturaMs = timeoutLecturaMs != null ? timeoutLecturaMs : TIMEOUT_LECTURA_POR_DEFECTO_MS;
        }

        /** Si hay que enviar autenticación básica (solo en staging de Open Food Facts). */
        public boolean usaAutenticacionStaging() {
            return usuarioStaging != null && claveStaging != null;
        }

        /** Sin la clave ni la contraseña de staging, para que nunca acaben en un log. */
        @Override
        public String toString() {
            return "Proveedor[baseUrl=" + baseUrl + ", searchUrl=" + searchUrl + ", userAgent=" + userAgent
                    + ", usuarioStaging=" + usuarioStaging + ", claveStaging=" + oculto(claveStaging)
                    + ", apiKey=" + oculto(apiKey) + ", timeoutConexionMs=" + timeoutConexionMs
                    + ", timeoutLecturaMs=" + timeoutLecturaMs + "]";
        }

        private static String vacioANulo(String valor) {
            return valor == null || valor.isBlank() ? null : valor.strip();
        }

        private static String oculto(String valor) {
            return valor == null ? "null" : "***";
        }
    }
}
