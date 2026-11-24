package com.nutrisport.seguimiento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Un pesaje. */
public record PesoResponse(UUID id, LocalDate fecha, BigDecimal pesoKg) {
}
