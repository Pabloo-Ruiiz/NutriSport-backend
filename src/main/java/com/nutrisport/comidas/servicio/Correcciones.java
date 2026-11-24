package com.nutrisport.comidas.servicio;

import java.math.BigDecimal;

/** Resumen de las diferencias entre los candidatos y la comida confirmada por el usuario. */
public record Correcciones(int aceptadosSinCambio, int gramosModificados, int alimentoCambiado, int eliminados,
        int anadidos, BigDecimal errorMedioGramosPct) {
}
