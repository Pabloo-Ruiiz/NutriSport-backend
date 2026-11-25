package com.nutrisport.integracion.usda;

import java.util.List;

/** Detalle de un alimento de USDA: sus nutrientes y sus medidas caseras. */
public record DetalleUsdaDto(AlimentoUsdaDto alimento, List<PorcionUsdaDto> porciones) {

    public DetalleUsdaDto {
        porciones = porciones == null ? List.of() : List.copyOf(porciones);
    }
}
