package com.nutrisport.integracion.themealdb;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Correspondencia entre las {@code strCategory} de TheMealDB y nuestras categorías de recetas: la misma que
 * {@code categoria.codigo_externo} en {@code R__categorias.sql} (un test comprueba que coinciden). Las categorías
 * de TheMealDB desconocidas van a "Otras", la equivalente de "Miscellaneous".
 */
public final class TablaCategoriasMealDb {

    /** "Otras" en R__categorias.sql (Miscellaneous). */
    public static final UUID OTRAS = categoria(19);

    private static final Map<String, UUID> TABLA = Map.ofEntries(
            Map.entry("Breakfast", categoria(1)),
            Map.entry("Chicken", categoria(7)),
            Map.entry("Beef", categoria(8)),
            Map.entry("Seafood", categoria(9)),
            Map.entry("Vegetarian", categoria(10)),
            Map.entry("Vegan", categoria(11)),
            Map.entry("Pasta", categoria(12)),
            Map.entry("Starter", categoria(13)),
            Map.entry("Side", categoria(14)),
            Map.entry("Dessert", categoria(15)),
            Map.entry("Lamb", categoria(16)),
            Map.entry("Pork", categoria(17)),
            Map.entry("Goat", categoria(18)),
            Map.entry("Miscellaneous", OTRAS));

    private static final Map<String, UUID> POR_CLAVE = TABLA.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(entrada -> clave(entrada.getKey()), Map.Entry::getValue));
    private static final Map<UUID, String> POR_CATEGORIA = TABLA.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private TablaCategoriasMealDb() {
    }

    /** Categoría propia de una strCategory de TheMealDB (sin distinguir mayúsculas), si la hay. */
    public static Optional<UUID> categoriaPara(String strCategory) {
        return strCategory == null ? Optional.empty() : Optional.ofNullable(POR_CLAVE.get(clave(strCategory)));
    }

    /** Como {@link #categoriaPara}, pero las desconocidas o ausentes van a "Otras". */
    public static UUID categoriaUOtras(String strCategory) {
        return categoriaPara(strCategory).orElse(OTRAS);
    }

    /** strCategory de TheMealDB de una categoría propia (para filtrar por categoría en TheMealDB). */
    public static Optional<String> codigoPara(UUID categoriaId) {
        return Optional.ofNullable(POR_CATEGORIA.get(categoriaId));
    }

    /** Toda la tabla (strCategory → categoría propia). */
    public static Map<String, UUID> tabla() {
        return TABLA;
    }

    private static String clave(String strCategory) {
        return strCategory.strip().toLowerCase(Locale.ROOT);
    }

    private static UUID categoria(int numero) {
        return UUID.fromString("c0000000-0000-0000-0001-%012d".formatted(numero));
    }
}
