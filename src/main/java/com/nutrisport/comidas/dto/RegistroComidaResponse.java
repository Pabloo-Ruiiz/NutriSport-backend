package com.nutrisport.comidas.dto;

import com.nutrisport.comidas.entidad.OrigenRegistro;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Comida registrada (§7.11). {@code avisos} recoge, por alimento, los conflictos con las restricciones
 * estrictas del usuario (alérgenos, trazas, alérgenos sin revisar, lactosa, dieta, rechazos): informan
 * pero no impiden el registro.
 */
public record RegistroComidaResponse(
        UUID id,
        LocalDate fecha,
        LocalTime hora,
        TipoComida tipoComida,
        OrigenRegistro origen,
        String notas,
        List<AlimentoRegistradoResponse> alimentos,
        Nutrientes totales,
        List<String> avisos) {
}
