package com.nutrisport.perfil.dto;

import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Perfil deportivo del usuario autenticado con sus objetivos base (sin entrenamiento). Los objetivos
 * son nulos mientras no se han calculado ni fijado a mano.
 */
public record PerfilResponse(
        LocalDate fechaNacimiento,
        Sexo sexo,
        BigDecimal alturaCm,
        BigDecimal pesoKg,
        BigDecimal porcentajeGrasa,
        ObjetivoDeportivo objetivo,
        LocalDate fechaCompeticion,
        String deportePrincipal,
        NivelActividad nivelActividad,
        Integer diasEntrenamientoSemana,
        Integer duracionMediaMin,
        Integer comidasPorDia,
        Integer tiempoMaxCocinaMin,
        BigDecimal objetivoKcal,
        BigDecimal objetivoProteinaG,
        BigDecimal objetivoCarbohidratosG,
        BigDecimal objetivoGrasaG,
        boolean objetivosManuales) {
}
