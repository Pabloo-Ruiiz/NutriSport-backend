package com.nutrisport.integracion.openfoodfacts;

import com.nutrisport.perfil.entidad.Alergeno;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Correspondencia entre las etiquetas de alérgenos de Open Food Facts ({@code allergens_tags} y
 * {@code traces_tags}) y los 14 alérgenos de declaración obligatoria en la UE de nuestro enum
 * {@link Alergeno} (docs/API_INTEGRATIONS.md §3.4). Las etiquetas desconocidas se ignoran; por eso los
 * alérgenos de un producto importado quedan siempre sin revisar hasta que los revisa un administrador.
 */
public final class TablaAlergenosOff {

    private static final Map<String, Alergeno> TABLA = Map.ofEntries(
            Map.entry("en:gluten", Alergeno.GLUTEN),
            Map.entry("en:milk", Alergeno.LACTEOS),
            Map.entry("en:eggs", Alergeno.HUEVOS),
            // "nuts" son los frutos de cáscara (almendra, avellana, nuez...); los cacahuetes van aparte
            Map.entry("en:nuts", Alergeno.FRUTOS_DE_CASCARA),
            Map.entry("en:peanuts", Alergeno.CACAHUETES),
            Map.entry("en:soybeans", Alergeno.SOJA),
            Map.entry("en:fish", Alergeno.PESCADO),
            Map.entry("en:crustaceans", Alergeno.CRUSTACEOS),
            Map.entry("en:molluscs", Alergeno.MOLUSCOS),
            Map.entry("en:celery", Alergeno.APIO),
            Map.entry("en:mustard", Alergeno.MOSTAZA),
            Map.entry("en:sesame-seeds", Alergeno.SESAMO),
            Map.entry("en:sulphur-dioxide-and-sulphites", Alergeno.SULFITOS),
            Map.entry("en:lupin", Alergeno.ALTRAMUCES));

    private TablaAlergenosOff() {
    }

    /** Alérgenos de las etiquetas, sin repetir y en el orden del enum; las desconocidas se ignoran. */
    public static Set<Alergeno> traducir(Collection<String> etiquetas) {
        Set<Alergeno> alergenos = EnumSet.noneOf(Alergeno.class);
        if (etiquetas == null) {
            return alergenos;
        }
        for (String etiqueta : etiquetas) {
            if (etiqueta != null) {
                Alergeno alergeno = TABLA.get(etiqueta.strip().toLowerCase(Locale.ROOT));
                if (alergeno != null) {
                    alergenos.add(alergeno);
                }
            }
        }
        return alergenos;
    }
}
