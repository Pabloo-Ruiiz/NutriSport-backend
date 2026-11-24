package com.nutrisport.catalogo.servicio;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementación provisional de {@link UsoAlimento} mientras no existen las comidas registradas: ningún
 * alimento está en uso, así que el borrado es físico. Se sustituye en el paso 15 por una que consulte
 * alimento_registrado.
 */
@Component
public class UsoAlimentoSinRegistros implements UsoAlimento {

    @Override
    public boolean estaEnUso(UUID alimentoId) {
        return false;
    }
}
