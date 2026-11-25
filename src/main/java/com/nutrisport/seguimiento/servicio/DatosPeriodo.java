package com.nutrisport.seguimiento.servicio;

import com.nutrisport.comun.dto.Nutrientes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Datos de un usuario en un periodo que analiza {@link DetectorPatrones} (§4.6): un elemento por cada día del periodo
 * (también los días sin registros) y los pesajes.
 *
 * @param dias    todos los días de {@code desde} a {@code hasta}, en orden
 * @param pesajes pesajes del periodo, en orden de fecha
 */
public record DatosPeriodo(LocalDate desde, LocalDate hasta, List<DiaDatos> dias, List<Pesaje> pesajes) {

    public DatosPeriodo {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        dias = List.copyOf(dias);
        pesajes = List.copyOf(pesajes);
    }

    /**
     * Un día del periodo.
     *
     * @param consumido         suma de las comidas registradas (cero si no hay)
     * @param objetivo          su ObjetivoDiario guardado; nulo si no tiene
     * @param diaEntreno        si tiene algún entrenamiento planificado o realizado
     * @param proteinaPorComida gramos de proteína de cada comida registrada (vacía si no hay registros)
     */
    public record DiaDatos(LocalDate fecha, Nutrientes consumido, Nutrientes objetivo, boolean diaEntreno,
            List<BigDecimal> proteinaPorComida) {

        public DiaDatos {
            Objects.requireNonNull(fecha, "fecha");
            consumido = consumido == null ? Nutrientes.CERO : consumido;
            proteinaPorComida = proteinaPorComida == null ? List.of() : List.copyOf(proteinaPorComida);
        }

        /** Número de comidas registradas. */
        public int registros() {
            return proteinaPorComida.size();
        }

        public boolean conRegistro() {
            return !proteinaPorComida.isEmpty();
        }
    }

    /** Un pesaje. */
    public record Pesaje(LocalDate fecha, BigDecimal pesoKg) {

        public Pesaje {
            Objects.requireNonNull(fecha, "fecha");
            Objects.requireNonNull(pesoKg, "pesoKg");
        }
    }
}
