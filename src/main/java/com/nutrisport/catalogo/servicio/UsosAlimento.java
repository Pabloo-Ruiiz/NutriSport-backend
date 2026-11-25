package com.nutrisport.catalogo.servicio;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reúne todas las implementaciones de {@link UsoAlimento} (comidas, recetas y, más adelante, menús): un
 * alimento está en uso si lo está en cualquiera de esos módulos. Así cada módulo aporta su comprobación sin que
 * el catálogo dependa de él.
 */
@Component
public class UsosAlimento {

    private final List<UsoAlimento> usos;

    public UsosAlimento(List<UsoAlimento> usos) {
        this.usos = List.copyOf(usos);
    }

    public boolean estaEnUso(UUID alimentoId) {
        return usos.stream().anyMatch(uso -> uso.estaEnUso(alimentoId));
    }
}
