package com.nutrisport.integracion.openfoodfacts;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Correspondencia entre algunas etiquetas de {@code categories_tags} de Open Food Facts y las categorías de
 * alimentos iniciales (identificadores fijos de {@code R__categorias.sql}). Solo se reconocen etiquetas
 * exactas y generales; un producto sin ninguna reconocida va a "Otros" (§3.4).
 * <p>
 * Open Food Facts ordena las etiquetas de la más general a la más concreta: gana la primera reconocida
 * (p. ej. un batido con "en:dairies" y "en:beverages" queda en Lácteos).
 */
public final class TablaCategoriasOff {

    private static final Map<String, UUID> TABLA = Map.ofEntries(
            Map.entry("en:fruits", categoria(1)),
            Map.entry("en:vegetables", categoria(2)),
            Map.entry("en:meats", categoria(3)),
            Map.entry("en:fishes", categoria(4)),
            Map.entry("en:seafood", categoria(4)),
            Map.entry("en:eggs", categoria(5)),
            Map.entry("en:dairies", categoria(6)),
            Map.entry("en:cereals-and-their-products", categoria(7)),
            Map.entry("en:breads", categoria(7)),
            Map.entry("en:legumes", categoria(8)),
            Map.entry("en:nuts", categoria(9)),
            Map.entry("en:fats", categoria(10)),
            Map.entry("en:beverages", categoria(11)),
            Map.entry("en:frozen-foods", categoria(12)),
            Map.entry("en:dietary-supplements", categoria(13)));

    /** "Otros" en R__categorias.sql. */
    public static final UUID OTROS = categoria(14);

    private TablaCategoriasOff() {
    }

    /** Categoría de la primera etiqueta reconocida, si hay alguna. */
    public static Optional<UUID> categoriaPara(Collection<String> etiquetas) {
        if (etiquetas == null) {
            return Optional.empty();
        }
        return etiquetas.stream()
                .filter(etiqueta -> etiqueta != null)
                .map(etiqueta -> TABLA.get(etiqueta.strip().toLowerCase(Locale.ROOT)))
                .filter(categoria -> categoria != null)
                .findFirst();
    }

    private static UUID categoria(int numero) {
        return UUID.fromString("c0000000-0000-0000-0000-%012d".formatted(numero));
    }
}
