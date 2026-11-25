package com.nutrisport.menus.recomendacion;

import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import java.util.Set;
import java.util.UUID;

/**
 * Datos del usuario que el motor necesita para generar sus días (§4.5).
 *
 * @param restricciones    restricciones estrictas (filtro duro); nulas = ninguna
 * @param recetasFavoritas recetas que puntúan como favoritas (−15)
 * @param tiempoMaxMin     tiempo máximo de cocina del perfil en minutos; nulo = sin límite
 * @param comidasPorDia    comidas al día del perfil (3 a 6), que fijan las franjas ({@link RepartoFranjas})
 */
public record ContextoGeneracion(RestriccionesUsuario restricciones, Set<UUID> recetasFavoritas, Integer tiempoMaxMin,
        int comidasPorDia) {

    public ContextoGeneracion {
        restricciones = restricciones == null ? RestriccionesUsuario.NINGUNA : restricciones;
        recetasFavoritas = recetasFavoritas == null ? Set.of() : Set.copyOf(recetasFavoritas);
        if (comidasPorDia < RepartoFranjas.MINIMO_COMIDAS || comidasPorDia > RepartoFranjas.MAXIMO_COMIDAS) {
            throw new IllegalArgumentException("Las comidas al día deben estar entre 3 y 6: " + comidasPorDia);
        }
    }

    public boolean esFavorita(UUID recetaId) {
        return recetaId != null && recetasFavoritas.contains(recetaId);
    }
}
