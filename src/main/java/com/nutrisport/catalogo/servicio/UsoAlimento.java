package com.nutrisport.catalogo.servicio;

import java.util.UUID;

/**
 * Indica si un alimento ya se ha usado (en comidas registradas y, en la Ampliación, en recetas o
 * menús). Si lo está, al borrarlo solo se desactiva para no romper el historial (§6.3).
 * <p>
 * Es una interfaz para que el catálogo no dependa de los módulos que usan alimentos: cada uno aporta
 * su implementación.
 */
public interface UsoAlimento {

    boolean estaEnUso(UUID alimentoId);
}
