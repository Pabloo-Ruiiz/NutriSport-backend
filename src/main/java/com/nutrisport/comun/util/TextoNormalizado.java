package com.nutrisport.comun.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización de texto para búsquedas tolerantes a tildes y mayúsculas (§6.1, nombre_normalizado
 * de alimento). La misma función se aplica al guardar y al buscar, así que ambos lados coinciden.
 */
public final class TextoNormalizado {

    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");
    private static final Pattern COMODINES_LIKE = Pattern.compile("[%_\\\\]");

    private TextoNormalizado() {
    }

    /**
     * Minúsculas, sin tildes ni diéresis (también la ñ pasa a n, para que "pina" encuentre "piña") y
     * con los espacios colapsados y recortados. Devuelve {@code null} si recibe {@code null}.
     * <p>
     * Ejemplo: {@code "  Plátano   de Canarias "} → {@code "platano de canarias"}.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String sinMarcas = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACIOS.matcher(sinMarcas.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /**
     * Como {@link #normalizar} pero quitando también los comodines de LIKE ({@code %}, {@code _} y la
     * barra invertida), para buscar alimentos por el texto y no por un patrón.
     */
    public static String paraBusqueda(String texto) {
        return normalizar(texto == null ? null : COMODINES_LIKE.matcher(texto).replaceAll(" "));
    }
}
