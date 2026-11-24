package com.nutrisport.actividad.dto;

import java.math.BigDecimal;

/** Actividad del catálogo con su MET (GET /api/v1/entrenamientos/tipos, §7.8). */
public record TipoActividadResponse(String codigo, String nombre, BigDecimal met, String categoria) {
}
