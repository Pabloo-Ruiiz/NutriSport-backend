package com.nutrisport.perfil.servicio;

import com.nutrisport.perfil.dto.ObjetivosCalculados;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Calcula los objetivos diarios base (sin entrenamiento) a partir del perfil, con programación
 * convencional y sin acceso a base de datos (§3.B; reglas fijadas en el paso 12 de la guía):
 * <ol>
 * <li>Metabolismo basal (Mifflin-St Jeor): {@code 10·kg + 6,25·cm − 5·edad + 5} (hombre) o
 * {@code − 161} (mujer).</li>
 * <li>Gasto sin entrenamiento = basal × factor de actividad (1,2 · 1,3 · 1,4 · 1,5). El gasto de los
 * entrenamientos se suma aparte cada día.</li>
 * <li>Ajuste por objetivo: perder grasa −15 %, ganar músculo +10 %, resto 0 %.</li>
 * <li>Seguridad: déficit máximo del 25 % del gasto y mínimo de 1500 kcal (hombre) o 1200 kcal (mujer);
 * cada límite aplicado añade un aviso.</li>
 * <li>Proteína por kg según el objetivo; grasa = 25 % de la energía; carbohidratos = el resto
 * {@code (kcal − P·4 − G·9) / 4} con P y G ya redondeados.</li>
 * </ol>
 * kcal y gramos se redondean a entero con {@code HALF_UP}; los cálculos intermedios no se redondean.
 */
@Component
public class CalculadoraObjetivos {

    static final BigDecimal KCAL_MINIMAS_HOMBRE = new BigDecimal("1500");
    static final BigDecimal KCAL_MINIMAS_MUJER = new BigDecimal("1200");
    static final BigDecimal DEFICIT_MAXIMO = new BigDecimal("0.25");
    static final BigDecimal FRACCION_GRASA = new BigDecimal("0.25");
    static final BigDecimal DESVIACION_MAXIMA_MACROS = new BigDecimal("0.10");

    private static final BigDecimal KCAL_POR_G_PROTEINA = new BigDecimal("4");
    private static final BigDecimal KCAL_POR_G_CARBOHIDRATO = new BigDecimal("4");
    private static final BigDecimal KCAL_POR_G_GRASA = new BigDecimal("9");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int DECIMALES_DESGLOSE = 2;

    public ObjetivosCalculados calcular(PerfilDeportivo perfil, LocalDate hoy) {
        List<String> avisos = new ArrayList<>();

        int edad = Period.between(perfil.getFechaNacimiento(), hoy).getYears();
        BigDecimal metabolismoBasal = metabolismoBasal(perfil.getSexo(), perfil.getPesoActualKg(),
                perfil.getAlturaCm(), edad);
        BigDecimal factor = factorActividad(perfil.getNivelActividad());
        BigDecimal gastoSinEntreno = metabolismoBasal.multiply(factor);
        int ajustePorcentaje = ajustePorcentaje(perfil.getObjetivo());

        BigDecimal energia = gastoSinEntreno
                .multiply(CIEN.add(BigDecimal.valueOf(ajustePorcentaje)))
                .divide(CIEN);

        // Límites de seguridad: primero el déficit máximo y después el mínimo absoluto
        BigDecimal energiaMinimaPorDeficit = gastoSinEntreno.multiply(BigDecimal.ONE.subtract(DEFICIT_MAXIMO));
        if (energia.compareTo(energiaMinimaPorDeficit) < 0) {
            energia = energiaMinimaPorDeficit;
            avisos.add("Se ha limitado el déficit al 25 % del gasto energético");
        }
        BigDecimal minimo = kcalMinimas(perfil.getSexo());
        if (energia.compareTo(minimo) < 0) {
            energia = minimo;
            avisos.add("La energía se ha elevado al mínimo de seguridad de " + minimo.toPlainString() + " kcal");
        }

        BigDecimal kcal = entero(energia);
        BigDecimal proteinaG = entero(proteinaPorKg(perfil.getObjetivo()).multiply(perfil.getPesoActualKg()));
        BigDecimal grasaG = entero(kcal.multiply(FRACCION_GRASA).divide(KCAL_POR_G_GRASA, 10, RoundingMode.HALF_UP));
        BigDecimal kcalRestantes = kcal
                .subtract(proteinaG.multiply(KCAL_POR_G_PROTEINA))
                .subtract(grasaG.multiply(KCAL_POR_G_GRASA));
        BigDecimal carbohidratosG = entero(kcalRestantes.divide(KCAL_POR_G_CARBOHIDRATO, 10, RoundingMode.HALF_UP));
        if (carbohidratosG.signum() < 0) {
            // Salvaguarda: con los rangos admitidos del perfil no debería ocurrir
            carbohidratosG = BigDecimal.ZERO;
            avisos.add("La proteína y la grasa cubren toda la energía: los carbohidratos se han fijado en 0 g");
        }

        return new ObjetivosCalculados(
                edad,
                metabolismoBasal.setScale(DECIMALES_DESGLOSE, RoundingMode.HALF_UP),
                factor,
                gastoSinEntreno.setScale(DECIMALES_DESGLOSE, RoundingMode.HALF_UP),
                ajustePorcentaje,
                kcal,
                proteinaG,
                carbohidratosG,
                grasaG,
                avisos);
    }

    /**
     * Avisos de unos objetivos fijados a mano (avisan, no bloquean): la energía de los macros
     * ({@code P·4 + HC·4 + G·9}) se aleja más de un 10 % de las kcal, o las kcal están por debajo del
     * mínimo de seguridad del sexo.
     */
    public List<String> avisosObjetivosManuales(Sexo sexo, BigDecimal kcal, BigDecimal proteinaG,
            BigDecimal carbohidratosG, BigDecimal grasaG) {
        List<String> avisos = new ArrayList<>();
        BigDecimal energiaMacros = proteinaG.multiply(KCAL_POR_G_PROTEINA)
                .add(carbohidratosG.multiply(KCAL_POR_G_CARBOHIDRATO))
                .add(grasaG.multiply(KCAL_POR_G_GRASA));
        BigDecimal desviacionMaxima = kcal.multiply(DESVIACION_MAXIMA_MACROS);
        if (energiaMacros.subtract(kcal).abs().compareTo(desviacionMaxima) > 0) {
            avisos.add("La energía de los macronutrientes (" + entero(energiaMacros).toPlainString()
                    + " kcal) se aleja más de un 10 % de la energía objetivo (" + kcal.stripTrailingZeros()
                    .toPlainString() + " kcal)");
        }
        BigDecimal minimo = kcalMinimas(sexo);
        if (kcal.compareTo(minimo) < 0) {
            avisos.add("La energía objetivo está por debajo del mínimo de seguridad de "
                    + minimo.toPlainString() + " kcal");
        }
        return avisos;
    }

    /** Mínimo de seguridad de energía diaria: 1500 kcal (hombre) o 1200 kcal (mujer). */
    public static BigDecimal kcalMinimas(Sexo sexo) {
        return sexo == Sexo.HOMBRE ? KCAL_MINIMAS_HOMBRE : KCAL_MINIMAS_MUJER;
    }

    /** Mifflin-St Jeor:{@code 10·kg + 6,25·cm − 5·edad + 5} (hombre) / {@code − 161} (mujer). */
    static BigDecimal metabolismoBasal(Sexo sexo, BigDecimal pesoKg, BigDecimal alturaCm, int edad) {
        BigDecimal constante = sexo == Sexo.HOMBRE ? new BigDecimal("5") : new BigDecimal("-161");
        return new BigDecimal("10").multiply(pesoKg)
                .add(new BigDecimal("6.25").multiply(alturaCm))
                .subtract(new BigDecimal("5").multiply(BigDecimal.valueOf(edad)))
                .add(constante);
    }

    /** Factor por la actividad fuera del entrenamiento (el entrenamiento se suma aparte). */
    static BigDecimal factorActividad(NivelActividad nivel) {
        return switch (nivel) {
            case SEDENTARIO -> new BigDecimal("1.2");
            case LIGERO -> new BigDecimal("1.3");
            case MODERADO -> new BigDecimal("1.4");
            case ALTO -> new BigDecimal("1.5");
        };
    }

    static int ajustePorcentaje(ObjetivoDeportivo objetivo) {
        return switch (objetivo) {
            case PERDER_GRASA -> -15;
            case GANAR_MUSCULO -> 10;
            case MANTENER, RENDIMIENTO, COMPETICION -> 0;
        };
    }

    static BigDecimal proteinaPorKg(ObjetivoDeportivo objetivo) {
        return switch (objetivo) {
            case PERDER_GRASA -> new BigDecimal("2.0");
            case GANAR_MUSCULO -> new BigDecimal("1.8");
            case RENDIMIENTO, COMPETICION -> new BigDecimal("1.6");
            case MANTENER -> new BigDecimal("1.4");
        };
    }

    private static BigDecimal entero(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP);
    }
}
