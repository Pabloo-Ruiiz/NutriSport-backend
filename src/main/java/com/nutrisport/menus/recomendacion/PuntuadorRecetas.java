package com.nutrisport.menus.recomendacion;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.recetas.entidad.Receta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Puntuación de una receta para una franja (§4.5); <b>menor es mejor</b>:
 * <pre>
 * 0,5·|Δkcal%| + 0,3·|Δproteína%| + 0,1·|ΔHC%| + 0,1·|Δgrasa%|   (respecto al objetivo de la franja)
 * − 15  si es favorita
 * + 25  si la receta se usó en los 2 días anteriores
 * − 10  si es día de entreno y la receta tiene la etiqueta "pre-entreno" (solo en la MERIENDA) o "post-entreno"
 * </pre>
 * Δx% = (aportado − objetivo) / objetivo × 100. Si el objetivo de un nutriente es 0, la desviación es 0 si no se
 * aporta nada y 100 si se aporta algo. Las raciones posibles son 0,75 · 1 · 1,25 · 1,5 · 2 y se elige la que mejor
 * ajusta a la franja ({@link #mejoresRaciones}).
 */
public final class PuntuadorRecetas {

    /** Raciones que puede proponer el motor, de menor a mayor. */
    public static final List<BigDecimal> RACIONES = List.of(new BigDecimal("0.75"), BigDecimal.ONE,
            new BigDecimal("1.25"), new BigDecimal("1.5"), new BigDecimal("2"));

    static final double PESO_KCAL = 0.5;
    static final double PESO_PROTEINA = 0.3;
    static final double PESO_CARBOHIDRATOS = 0.1;
    static final double PESO_GRASA = 0.1;
    static final double BONO_FAVORITA = -15;
    static final double PENALIZACION_REPETIDA = 25;
    static final double BONO_ENTRENO = -10;

    public static final String ETIQUETA_PRE_ENTRENO = "pre-entreno";
    public static final String ETIQUETA_POST_ENTRENO = "post-entreno";

    private static final double CIEN = 100;

    private PuntuadorRecetas() {
    }

    /**
     * @param raciones        raciones de la receta
     * @param objetivoFranja  lo que debería aportar la franja
     * @param usadasRecientes recetas usadas en los 2 días anteriores
     * @param tipoComida      franja para la que se puntúa
     */
    public static double puntuar(Receta receta, BigDecimal raciones, Nutrientes objetivoFranja,
            ContextoGeneracion contexto, Set<UUID> usadasRecientes, boolean diaEntreno, TipoComida tipoComida) {
        double puntuacion = ajuste(nutrientesPorRacion(receta).escalar(raciones), objetivoFranja);
        if (contexto.esFavorita(receta.getId())) {
            puntuacion += BONO_FAVORITA;
        }
        if (receta.getId() != null && usadasRecientes.contains(receta.getId())) {
            puntuacion += PENALIZACION_REPETIDA;
        }
        if (diaEntreno && esDeEntreno(receta, tipoComida)) {
            puntuacion += BONO_ENTRENO;
        }
        return puntuacion;
    }

    /** Parte de la puntuación que mide el ajuste a los macros: 0,5·|Δkcal%| + 0,3·|Δprot%| + 0,1·|ΔHC%| + 0,1·|Δgrasa%|. */
    public static double ajuste(Nutrientes aportado, Nutrientes objetivo) {
        return PESO_KCAL * desviacionPct(aportado.kcal(), objetivo.kcal())
                + PESO_PROTEINA * desviacionPct(aportado.proteina(), objetivo.proteina())
                + PESO_CARBOHIDRATOS * desviacionPct(aportado.carbohidratos(), objetivo.carbohidratos())
                + PESO_GRASA * desviacionPct(aportado.grasa(), objetivo.grasa());
    }

    /** |aportado − objetivo| / objetivo × 100; con objetivo ≤ 0, 0 si no se aporta nada y 100 si se aporta algo. */
    public static double desviacionPct(BigDecimal aportado, BigDecimal objetivo) {
        if (objetivo.signum() <= 0) {
            return aportado.signum() > 0 ? CIEN : 0;
        }
        return Math.abs(aportado.subtract(objetivo).doubleValue()) / objetivo.doubleValue() * CIEN;
    }

    /**
     * Las raciones de {@link #RACIONES} que mejor ajustan la receta al objetivo de la franja (menor {@link #ajuste});
     * en caso de empate, las menores. El resto de la puntuación no depende de las raciones.
     */
    public static BigDecimal mejoresRaciones(Receta receta, Nutrientes objetivoFranja) {
        Nutrientes porRacion = nutrientesPorRacion(receta);
        BigDecimal mejores = RACIONES.get(0);
        double mejorAjuste = Double.MAX_VALUE;
        for (BigDecimal raciones : RACIONES) {
            double ajuste = ajuste(porRacion.escalar(raciones), objetivoFranja);
            if (ajuste < mejorAjuste) {
                mejorAjuste = ajuste;
                mejores = raciones;
            }
        }
        return mejores;
    }

    /** Nutrientes de una ración; cero si la receta no tiene cálculo (no puede ser candidata). */
    public static Nutrientes nutrientesPorRacion(Receta receta) {
        if (receta.getKcalRacion() == null || receta.getProteinaRacion() == null
                || receta.getCarbohidratosRacion() == null || receta.getGrasaRacion() == null) {
            return Nutrientes.CERO;
        }
        return new Nutrientes(receta.getKcalRacion(), receta.getProteinaRacion(), receta.getCarbohidratosRacion(),
                receta.getGrasaRacion());
    }

    /** "pre-entreno" cuenta solo en la MERIENDA; "post-entreno", en cualquier franja. Sin distinguir mayúsculas. */
    static boolean esDeEntreno(Receta receta, TipoComida tipoComida) {
        boolean pre = false;
        boolean post = false;
        for (String etiqueta : receta.getEtiquetas()) {
            String normalizada = etiqueta == null ? "" : etiqueta.strip().toLowerCase(Locale.ROOT);
            pre |= ETIQUETA_PRE_ENTRENO.equals(normalizada);
            post |= ETIQUETA_POST_ENTRENO.equals(normalizada);
        }
        return (pre && tipoComida == TipoComida.MERIENDA) || post;
    }
}
