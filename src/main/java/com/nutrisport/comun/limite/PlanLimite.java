package com.nutrisport.comun.limite;

import java.time.Duration;

/** Límites de peticiones del proyecto (§3.A y §8.1): capacidad por periodo y por clave (email o IP). */
public enum PlanLimite {

    /** Intentos de login fallidos: 5 cada 15 minutos por email y por IP. */
    LOGIN_FALLIDO(5, Duration.ofMinutes(15)),

    /** Registros y reenvíos de verificación: 3 por hora por IP. */
    REGISTRO(3, Duration.ofHours(1)),

    /** Solicitudes de recuperación de contraseña: 3 por hora por IP. */
    RECUPERACION(3, Duration.ofHours(1));

    private final long capacidad;
    private final Duration periodo;

    PlanLimite(long capacidad, Duration periodo) {
        this.capacidad = capacidad;
        this.periodo = periodo;
    }

    public long getCapacidad() {
        return capacidad;
    }

    public Duration getPeriodo() {
        return periodo;
    }
}
