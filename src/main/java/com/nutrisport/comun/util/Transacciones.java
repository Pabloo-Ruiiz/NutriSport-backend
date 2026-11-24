package com.nutrisport.comun.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Utilidades de transacciones. */
public final class Transacciones {

    private Transacciones() {
    }

    /**
     * Ejecuta la acción cuando la transacción actual se confirma (p. ej. enviar un correo con un
     * enlace: si la transacción se deshace, no se envía). Sin transacción activa, la ejecuta ya.
     */
    public static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
