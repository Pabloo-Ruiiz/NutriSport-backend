package com.nutrisport.comun.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Similitud entre el nombre de un ingrediente y el de un alimento candidato, de 0 a 1, para elegir el
 * emparejamiento de un ingrediente (docs/API_INTEGRATIONS.md §7.1). Combina a partes iguales:
 * <ul>
 * <li><b>trigramas</b>: coincidencia de grupos de tres letras, como {@code pg_trgm} (tolera erratas y el orden
 * de las palabras);</li>
 * <li><b>cobertura</b>: qué parte de las palabras del ingrediente aparece en el candidato, aceptando plurales
 * sencillos ("spice" y "spices"). USDA describe los alimentos con muchas palabras ("Chicken, broilers or fryers,
 * breast, meat only, raw"), que hunden la similitud de trigramas aunque estén todas las del ingrediente.</li>
 * </ul>
 * Los textos se comparan en minúsculas y sin tildes.
 */
public final class SimilitudTexto {

    private SimilitudTexto() {
    }

    /** Similitud entre 0 y 1, con dos decimales. */
    public static BigDecimal confianza(String ingrediente, String candidato) {
        List<String> buscadas = palabras(ingrediente);
        List<String> candidatas = palabras(candidato);
        if (buscadas.isEmpty() || candidatas.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        double valor = 0.5 * trigramas(buscadas, candidatas) + 0.5 * cobertura(buscadas, candidatas);
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Palabras en minúsculas y sin tildes, sin signos de puntuación. */
    static List<String> palabras(String texto) {
        if (texto == null) {
            return List.of();
        }
        return Arrays.stream(TextoNormalizado.normalizar(texto).split("[^a-z0-9]+"))
                .filter(palabra -> !palabra.isEmpty())
                .toList();
    }

    /** Coeficiente de Jaccard de los trigramas de las palabras (cada palabra con dos espacios delante y uno detrás). */
    static double trigramas(List<String> a, List<String> b) {
        Set<String> trigramasA = trigramas(a);
        Set<String> trigramasB = trigramas(b);
        Set<String> comunes = new HashSet<>(trigramasA);
        comunes.retainAll(trigramasB);
        Set<String> todos = new HashSet<>(trigramasA);
        todos.addAll(trigramasB);
        return todos.isEmpty() ? 0 : (double) comunes.size() / todos.size();
    }

    /** Fracción de las palabras buscadas que aparecen en las candidatas. */
    static double cobertura(List<String> buscadas, List<String> candidatas) {
        long encontradas = buscadas.stream()
                .filter(buscada -> candidatas.stream().anyMatch(candidata -> mismaPalabra(buscada, candidata)))
                .count();
        return (double) encontradas / buscadas.size();
    }

    /** Iguales, o una es la otra con hasta dos letras más al final (plurales: spice/spices, tomato/tomatoes). */
    static boolean mismaPalabra(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String corta = a.length() <= b.length() ? a : b;
        String larga = a.length() <= b.length() ? b : a;
        return corta.length() >= 3 && larga.length() - corta.length() <= 2 && larga.startsWith(corta);
    }

    private static Set<String> trigramas(List<String> palabras) {
        Set<String> resultado = new HashSet<>();
        for (String palabra : palabras) {
            String rellena = "  " + palabra + " ";
            for (int i = 0; i + 3 <= rellena.length(); i++) {
                resultado.add(rellena.substring(i, i + 3));
            }
        }
        return resultado;
    }
}
