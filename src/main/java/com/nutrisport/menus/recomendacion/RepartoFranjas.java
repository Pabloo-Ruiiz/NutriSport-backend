package com.nutrisport.menus.recomendacion;

import com.nutrisport.comidas.entidad.TipoComida;
import java.math.BigDecimal;
import java.util.LinkedHashMap;

/**
 * Reparto de la energía del día entre las franjas según las comidas al día del perfil (§4.5), en el orden en que se
 * comen:
 * <table>
 * <caption>Porcentaje del día por franja</caption>
 * <tr><th>Comidas</th><th>Reparto</th></tr>
 * <tr><td>3</td><td>DESAYUNO 25 · COMIDA 40 · CENA 35</td></tr>
 * <tr><td>4</td><td>DESAYUNO 25 · COMIDA 35 · MERIENDA 15 · CENA 25</td></tr>
 * <tr><td>5</td><td>DESAYUNO 20 · MEDIA_MANANA 10 · COMIDA 30 · MERIENDA 15 · CENA 25</td></tr>
 * <tr><td>6</td><td>como 5 + POST_ENTRENO 10, restado a la comida (COMIDA 20)</td></tr>
 * </table>
 * Los porcentajes de cada reparto suman 100.
 */
public final class RepartoFranjas {

    public static final int MINIMO_COMIDAS = 3;
    public static final int MAXIMO_COMIDAS = 6;

    private RepartoFranjas() {
    }

    /**
     * @return un mapa nuevo (se puede modificar) de franja → porcentaje del día, en orden cronológico
     * @throws IllegalArgumentException si las comidas al día no están entre 3 y 6
     */
    public static LinkedHashMap<TipoComida, BigDecimal> de(int comidasPorDia) {
        LinkedHashMap<TipoComida, BigDecimal> reparto = new LinkedHashMap<>();
        switch (comidasPorDia) {
            case 3 -> {
                reparto.put(TipoComida.DESAYUNO, pct(25));
                reparto.put(TipoComida.COMIDA, pct(40));
                reparto.put(TipoComida.CENA, pct(35));
            }
            case 4 -> {
                reparto.put(TipoComida.DESAYUNO, pct(25));
                reparto.put(TipoComida.COMIDA, pct(35));
                reparto.put(TipoComida.MERIENDA, pct(15));
                reparto.put(TipoComida.CENA, pct(25));
            }
            case 5 -> {
                reparto.put(TipoComida.DESAYUNO, pct(20));
                reparto.put(TipoComida.MEDIA_MANANA, pct(10));
                reparto.put(TipoComida.COMIDA, pct(30));
                reparto.put(TipoComida.MERIENDA, pct(15));
                reparto.put(TipoComida.CENA, pct(25));
            }
            case 6 -> {
                reparto.put(TipoComida.DESAYUNO, pct(20));
                reparto.put(TipoComida.MEDIA_MANANA, pct(10));
                reparto.put(TipoComida.COMIDA, pct(20));
                reparto.put(TipoComida.MERIENDA, pct(15));
                reparto.put(TipoComida.POST_ENTRENO, pct(10));
                reparto.put(TipoComida.CENA, pct(25));
            }
            default -> throw new IllegalArgumentException(
                    "Las comidas al día deben estar entre " + MINIMO_COMIDAS + " y " + MAXIMO_COMIDAS + ": "
                            + comidasPorDia);
        }
        return reparto;
    }

    private static BigDecimal pct(int valor) {
        return BigDecimal.valueOf(valor);
    }
}
