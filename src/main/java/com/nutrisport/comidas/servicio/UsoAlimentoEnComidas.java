package com.nutrisport.comidas.servicio;

import com.nutrisport.catalogo.servicio.UsoAlimento;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Un alimento está en uso si aparece en alguna comida registrada (de cualquier usuario): entonces el
 * catálogo solo lo desactiva al borrarlo, porque la FK de alimento_registrado es RESTRICT (§6.3).
 */
@Component
public class UsoAlimentoEnComidas implements UsoAlimento {

    private final RegistroComidaRepositorio registroRepositorio;

    public UsoAlimentoEnComidas(RegistroComidaRepositorio registroRepositorio) {
        this.registroRepositorio = registroRepositorio;
    }

    @Override
    public boolean estaEnUso(UUID alimentoId) {
        return registroRepositorio.alimentoEnUso(alimentoId);
    }
}
