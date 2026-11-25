package com.nutrisport.catalogo.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analizador de reglas de las medidas de los ingredientes (texto libre de TheMealDB, docs/API_INTEGRATIONS.md
 * §7.1): números (enteros, decimales, mixtos "1 1/2", fracciones ASCII "3/4" y Unicode "½", rangos "1-2", que
 * toman la media), unidades de masa (g, kg, oz, lb), de volumen (ml, l, cup, tbsp/tblsp, tsp) con sus variantes, y
 * unidades contables ("1 chopped", "10", "clove"). "pinch", "to taste" y "garnish" son despreciables.
 * <p>
 * "handful" se devuelve como UNIDAD con unidad "handful": solo es despreciable si el ingrediente es una hierba
 * ({@link #esHierba}), y eso lo decide quien conoce el ingrediente. Sin librerías: solo expresiones regulares.
 */
public final class AnalizadorMedidas {

    /** Fracciones Unicode y su equivalente ASCII. */
    private static final Map<Character, String> FRACCIONES = Map.ofEntries(
            Map.entry('½', "1/2"), Map.entry('⅓', "1/3"), Map.entry('⅔', "2/3"), Map.entry('¼', "1/4"),
            Map.entry('¾', "3/4"), Map.entry('⅕', "1/5"), Map.entry('⅖', "2/5"), Map.entry('⅗', "3/5"),
            Map.entry('⅘', "4/5"), Map.entry('⅙', "1/6"), Map.entry('⅚', "5/6"), Map.entry('⅛', "1/8"),
            Map.entry('⅜', "3/8"), Map.entry('⅝', "5/8"), Map.entry('⅞', "7/8"));

    private static final Pattern DESPRECIABLE = Pattern.compile("\\b(pinch(es)?|to taste|garnish(ing)?)\\b");
    private static final Pattern PUNADO = Pattern.compile("\\bhandfuls?\\b");
    /** Medida entre paréntesis al principio del resto: "(12 oz.)". */
    private static final Pattern ENVASE = Pattern.compile("^\\(([^()]+)\\)");

    /**
     * Número al principio: rango ("1-2", "1 to 2"), mixto ("1 1/2"), fracción ("3/4") o decimal ("1.5", "1,5").
     * Grupos: 1 = primer número, 2 = segundo número del rango.
     */
    private static final String NUMERO = "(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)";
    private static final Pattern CANTIDAD = Pattern.compile("^" + NUMERO + "(?:\\s*(?:-|–|to)\\s*" + NUMERO + ")?");

    /** Variante → unidad canónica de masa. */
    private static final Map<String, String> MASA = Map.ofEntries(
            Map.entry("g", "g"), Map.entry("gr", "g"), Map.entry("grs", "g"), Map.entry("gram", "g"),
            Map.entry("grams", "g"), Map.entry("gramme", "g"), Map.entry("grammes", "g"),
            Map.entry("kg", "kg"), Map.entry("kgs", "kg"), Map.entry("kilo", "kg"), Map.entry("kilos", "kg"),
            Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("oz", "oz"), Map.entry("ounce", "oz"), Map.entry("ounces", "oz"),
            Map.entry("lb", "lb"), Map.entry("lbs", "lb"), Map.entry("pound", "lb"), Map.entry("pounds", "lb"));

    /** Variante → unidad canónica de volumen. */
    private static final Map<String, String> VOLUMEN = Map.ofEntries(
            Map.entry("ml", "ml"), Map.entry("mls", "ml"), Map.entry("millilitre", "ml"),
            Map.entry("millilitres", "ml"), Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"),
            Map.entry("l", "l"), Map.entry("litre", "l"), Map.entry("litres", "l"), Map.entry("liter", "l"),
            Map.entry("liters", "l"),
            Map.entry("cup", "cup"), Map.entry("cups", "cup"),
            Map.entry("tbsp", "tbsp"), Map.entry("tbsps", "tbsp"), Map.entry("tblsp", "tbsp"),
            Map.entry("tblsps", "tbsp"), Map.entry("tbs", "tbsp"), Map.entry("tbls", "tbsp"),
            Map.entry("tablespoon", "tbsp"), Map.entry("tablespoons", "tbsp"),
            Map.entry("tsp", "tsp"), Map.entry("tsps", "tsp"), Map.entry("teaspoon", "tsp"),
            Map.entry("teaspoons", "tsp"));

    /** Palabras de unidades contables que se reconocen ("1 clove", "2 large"...). */
    private static final Set<String> UNIDADES = Set.of("clove", "cloves", "chopped", "sliced", "diced", "minced",
            "whole", "medium", "large", "small", "piece", "pieces", "slice", "slices");

    /** Gramos de cada unidad de masa. */
    private static final Map<String, BigDecimal> GRAMOS = Map.of(
            "g", BigDecimal.ONE, "kg", new BigDecimal("1000"), "oz", new BigDecimal("28.3495"),
            "lb", new BigDecimal("453.592"));

    /** Mililitros de cada unidad de volumen (densidad 1: los mismos gramos). */
    private static final Map<String, BigDecimal> MILILITROS = Map.of(
            "ml", BigDecimal.ONE, "l", new BigDecimal("1000"), "cup", new BigDecimal("240"),
            "tbsp", new BigDecimal("15"), "tsp", new BigDecimal("5"));

    /** Hierbas frescas: "a handful" de ellas es despreciable (§7.1). */
    private static final Set<String> HIERBAS = Set.of("coriander", "cilantro", "parsley", "basil", "mint", "dill",
            "chives", "chive", "thyme", "rosemary", "oregano", "sage", "tarragon", "chervil", "herbs", "herb");

    private AnalizadorMedidas() {
    }

    /** Interpreta una medida de TheMealDB ("200g", "1 tbsp", "2 tblsp ", "½ cup", "10", "pinch", "Handful"). */
    public static MedidaInterpretada interpretar(String medida) {
        if (medida == null || medida.isBlank()) {
            return MedidaInterpretada.DESCONOCIDA;
        }
        String texto = normalizar(medida);
        if (DESPRECIABLE.matcher(texto).find()) {
            return new MedidaInterpretada(BigDecimal.ZERO, primeraPalabra(DESPRECIABLE, texto), TipoMedida.DESPRECIABLE);
        }

        BigDecimal cantidad = null;
        String resto = texto;
        Matcher numero = CANTIDAD.matcher(texto);
        if (numero.find()) {
            cantidad = numero(numero.group(1));
            if (numero.group(2) != null) {
                cantidad = cantidad.add(numero(numero.group(2))).divide(BigDecimal.TWO, 4, RoundingMode.HALF_UP);
            }
            resto = texto.substring(numero.end()).strip();
        } else if (texto.startsWith("a ") || texto.startsWith("an ")) {
            cantidad = BigDecimal.ONE;
            resto = texto.substring(texto.indexOf(' ') + 1).strip();
        }

        // Tamaño del envase entre paréntesis: "1 (12 oz.) can", "2 (400g) tins" → envases × su cantidad
        Matcher envase = ENVASE.matcher(resto);
        if (envase.find()) {
            MedidaInterpretada interior = interpretar(envase.group(1));
            if (interior.tipo() == TipoMedida.MASA || interior.tipo() == TipoMedida.VOLUMEN) {
                BigDecimal envases = cantidad != null ? cantidad : BigDecimal.ONE;
                return new MedidaInterpretada(envases.multiply(interior.cantidad()), interior.unidad(), interior.tipo());
            }
        }

        if (PUNADO.matcher(resto).find()) {
            return new MedidaInterpretada(cantidad != null ? cantidad : BigDecimal.ONE, "handful", TipoMedida.UNIDAD);
        }
        String palabra = resto.isEmpty() ? "" : resto.split("[\\s.,()]+")[0];
        if (MASA.containsKey(palabra)) {
            return new MedidaInterpretada(uno(cantidad), MASA.get(palabra), TipoMedida.MASA);
        }
        if (VOLUMEN.containsKey(palabra)) {
            return new MedidaInterpretada(uno(cantidad), VOLUMEN.get(palabra), TipoMedida.VOLUMEN);
        }
        if (UNIDADES.contains(palabra)) {
            return new MedidaInterpretada(uno(cantidad), palabra, TipoMedida.UNIDAD);
        }
        if (cantidad != null) {
            // "10", "2 eggs", "1 can"...: unidades del propio alimento
            return new MedidaInterpretada(cantidad, "unidad", TipoMedida.UNIDAD);
        }
        return MedidaInterpretada.DESCONOCIDA;
    }

    /** Gramos de una medida de masa; nulo si no es de masa. */
    public static BigDecimal gramosDeMasa(MedidaInterpretada medida) {
        if (medida.tipo() != TipoMedida.MASA) {
            return null;
        }
        return medida.cantidad().multiply(GRAMOS.get(medida.unidad())).setScale(1, RoundingMode.HALF_UP);
    }

    /** Mililitros de una medida de volumen (con densidad 1, también los gramos); nulo si no es de volumen. */
    public static BigDecimal mililitros(MedidaInterpretada medida) {
        if (medida.tipo() != TipoMedida.VOLUMEN) {
            return null;
        }
        return medida.cantidad().multiply(MILILITROS.get(medida.unidad())).setScale(1, RoundingMode.HALF_UP);
    }

    /** Palabras con las que se puede llamar una unidad de volumen en las medidas caseras de USDA. */
    public static Set<String> nombresDeVolumen(String unidad) {
        return switch (unidad) {
            case "cup" -> Set.of("cup", "cups");
            case "tbsp" -> Set.of("tbsp", "tablespoon", "tablespoons");
            case "tsp" -> Set.of("tsp", "teaspoon", "teaspoons");
            case "ml" -> Set.of("ml");
            case "l" -> Set.of("l", "liter", "litre");
            default -> Set.of();
        };
    }

    /** Si el nombre del ingrediente es una hierba fresca ("Coriander", "fresh parsley"). */
    public static boolean esHierba(String ingrediente) {
        if (ingrediente == null) {
            return false;
        }
        for (String palabra : ingrediente.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (HIERBAS.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------

    /** Minúsculas, sin espacios sobrantes, con las fracciones Unicode en ASCII ("1½" → "1 1/2"). */
    private static String normalizar(String medida) {
        StringBuilder texto = new StringBuilder();
        for (char caracter : medida.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            String fraccion = FRACCIONES.get(caracter);
            if (fraccion != null) {
                if (!texto.isEmpty() && Character.isDigit(texto.charAt(texto.length() - 1))) {
                    texto.append(' ');
                }
                texto.append(fraccion);
            } else {
                texto.append(caracter);
            }
        }
        return texto.toString().replaceAll("\\s+", " ").strip();
    }

    /** "1 1/2" → 1.5; "3/4" → 0.75; "1,5" → 1.5. */
    private static BigDecimal numero(String texto) {
        String limpio = texto.strip().replace(',', '.');
        if (limpio.contains(" ")) {
            String[] partes = limpio.split("\\s+");
            return numero(partes[0]).add(numero(partes[1]));
        }
        if (limpio.contains("/")) {
            String[] partes = limpio.split("/");
            BigDecimal divisor = new BigDecimal(partes[1]);
            return divisor.signum() == 0 ? BigDecimal.ZERO
                    : new BigDecimal(partes[0]).divide(divisor, 4, RoundingMode.HALF_UP);
        }
        return new BigDecimal(limpio);
    }

    private static BigDecimal uno(BigDecimal cantidad) {
        return cantidad != null ? cantidad : BigDecimal.ONE;
    }

    /** Forma canónica de la expresión despreciable: "pinch", "garnish" o "to taste". */
    private static String primeraPalabra(Pattern patron, String texto) {
        Matcher matcher = patron.matcher(texto);
        if (!matcher.find()) {
            return null;
        }
        String expresion = matcher.group(1);
        if (expresion.startsWith("pinch")) {
            return "pinch";
        }
        return expresion.startsWith("garnish") ? "garnish" : "to taste";
    }
}
