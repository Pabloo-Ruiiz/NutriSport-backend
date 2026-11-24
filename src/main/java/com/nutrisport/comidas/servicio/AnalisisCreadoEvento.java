package com.nutrisport.comidas.servicio;

import java.util.UUID;

/** Se publica al crear un análisis; ProcesadorAnalisis lo procesa en segundo plano tras el commit. */
public record AnalisisCreadoEvento(UUID analisisId) {
}
