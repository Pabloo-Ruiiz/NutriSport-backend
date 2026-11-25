package com.nutrisport.recetas.servicio;

import com.nutrisport.catalogo.servicio.UsoAlimento;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Un alimento está en uso si es el ingrediente emparejado de alguna receta (de cualquier usuario): entonces el
 * catálogo solo lo desactiva al borrarlo, porque la FK de receta_ingrediente impide borrarlo (V12).
 */
@Component
public class UsoAlimentoEnRecetas implements UsoAlimento {

    private final RecetaRepositorio recetaRepositorio;

    public UsoAlimentoEnRecetas(RecetaRepositorio recetaRepositorio) {
        this.recetaRepositorio = recetaRepositorio;
    }

    @Override
    public boolean estaEnUso(UUID alimentoId) {
        return recetaRepositorio.alimentoEnUso(alimentoId);
    }
}
