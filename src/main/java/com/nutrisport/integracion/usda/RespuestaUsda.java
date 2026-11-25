package com.nutrisport.integracion.usda;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce las respuestas de USDA FoodData Central (un {@code Map} leído del JSON) a nuestros DTO, aceptando las
 * dos formas de los nutrientes (docs/API_INTEGRATIONS.md §5.2): en la búsqueda, {@code nutrientId},
 * {@code unitName} y {@code value}; en el detalle, {@code nutrient.{id, unitName}} y {@code amount}. Nunca lanza
 * excepciones: lo que no se puede leer queda nulo.
 */
final class RespuestaUsda {

    static final int ENERGIA = 1008;
    static final int PROTEINA = 1003;
    static final int GRASA = 1004;
    static final int HIDRATOS = 1005;
    /** La energía 1008 tiene que venir en kcal: USDA da también la energía en kJ (id 1062). */
    static final String KCAL = "KCAL";

    private RespuestaUsda() {
    }

    static List<Map<?, ?>> elementos(Map<String, Object> cuerpo, String clave) {
        if (cuerpo == null || !(cuerpo.get(clave) instanceof List<?> lista)) {
            return List.of();
        }
        List<Map<?, ?>> elementos = new ArrayList<>();
        for (Object elemento : lista) {
            if (elemento instanceof Map<?, ?> mapa) {
                elementos.add(mapa);
            }
        }
        return elementos;
    }

    /** Alimento de la búsqueda o del detalle; nulo si no trae fdcId o descripción. */
    static AlimentoUsdaDto aAlimento(Map<?, ?> alimento) {
        String fdcId = texto(alimento.get("fdcId"));
        String descripcion = texto(alimento.get("description"));
        if (fdcId == null || descripcion == null) {
            return null;
        }
        BigDecimal kcal = null;
        BigDecimal proteinas = null;
        BigDecimal hidratos = null;
        BigDecimal grasas = null;
        List<?> nutrientes = alimento.get("foodNutrients") instanceof List<?> lista ? lista : List.of();
        for (Object elemento : nutrientes) {
            if (!(elemento instanceof Map<?, ?> nutriente)) {
                continue;
            }
            Map<?, ?> anidado = nutriente.get("nutrient") instanceof Map<?, ?> mapa ? mapa : Map.of();
            BigDecimal id = decimal(primero(nutriente.get("nutrientId"), anidado.get("id")));
            String unidad = texto(primero(nutriente.get("unitName"), anidado.get("unitName")));
            BigDecimal valor = decimal(primero(nutriente.get("value"), nutriente.get("amount")));
            if (id == null || valor == null) {
                continue;
            }
            switch (id.intValue()) {
                case ENERGIA -> {
                    if (KCAL.equalsIgnoreCase(unidad)) {
                        kcal = valor;
                    }
                }
                case PROTEINA -> proteinas = valor;
                case GRASA -> grasas = valor;
                case HIDRATOS -> hidratos = valor;
                default -> {
                    // Otro nutriente: no se usa
                }
            }
        }
        return new AlimentoUsdaDto(fdcId, descripcion, texto(alimento.get("dataType")), kcal, proteinas, hidratos,
                grasas);
    }

    /** Medidas caseras de {@code foodPortions}; se descartan las que no tienen gramos o descripción. */
    static List<PorcionUsdaDto> porciones(Map<?, ?> detalle) {
        List<PorcionUsdaDto> porciones = new ArrayList<>();
        List<?> lista = detalle.get("foodPortions") instanceof List<?> valores ? valores : List.of();
        for (Object elemento : lista) {
            if (!(elemento instanceof Map<?, ?> porcion)) {
                continue;
            }
            Map<?, ?> unidad = porcion.get("measureUnit") instanceof Map<?, ?> mapa ? mapa : Map.of();
            String descripcion = texto(primero(porcion.get("modifier"), porcion.get("portionDescription"),
                    unidad.get("name")));
            BigDecimal gramos = decimal(porcion.get("gramWeight"));
            BigDecimal cantidad = decimal(porcion.get("amount"));
            if (descripcion != null && gramos != null && gramos.signum() > 0) {
                porciones.add(new PorcionUsdaDto(descripcion,
                        cantidad != null && cantidad.signum() > 0 ? cantidad : BigDecimal.ONE, gramos));
            }
        }
        return porciones;
    }

    private static Object primero(Object... valores) {
        for (Object valor : valores) {
            if (valor != null && !(valor instanceof String cadena && cadena.isBlank())) {
                return valor;
            }
        }
        return null;
    }

    static String texto(Object valor) {
        if (valor instanceof String cadena) {
            return cadena.isBlank() ? null : cadena.strip();
        }
        if (valor instanceof Integer || valor instanceof Long) {
            return valor.toString();
        }
        if (valor instanceof Number numero) {
            return new BigDecimal(numero.toString()).stripTrailingZeros().toPlainString();
        }
        return null;
    }

    static BigDecimal decimal(Object valor) {
        try {
            if (valor instanceof Number numero) {
                return new BigDecimal(numero.toString());
            }
            if (valor instanceof String cadena && !cadena.isBlank()) {
                return new BigDecimal(cadena.strip());
            }
        } catch (NumberFormatException e) {
            // dato no disponible
        }
        return null;
    }
}
