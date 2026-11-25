package com.nutrisport.menus.dto;

import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Un día del menú frente a su objetivo (§7.5).
 *
 * @param objetivo           objetivo del día (base del perfil + entrenamientos); nulo si el usuario no tiene perfil
 * @param planificado        suma de los nutrientes de las comidas del día con cálculo
 * @param porcentajeObjetivo kcal planificadas sobre las del objetivo (0 sin objetivo)
 * @param diaEntreno         si hay entrenamientos planificados o realizados ese día
 */
public record DiaMenuResponse(LocalDate fecha, ObjetivoDiarioResponse objetivo, Nutrientes planificado,
        int porcentajeObjetivo, boolean diaEntreno, List<ComidaPlanificadaResponse> comidas) {

    public DiaMenuResponse {
        comidas = List.copyOf(comidas);
    }
}
