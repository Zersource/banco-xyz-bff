package com.duoc.cuentas.saga.repository;

/**
 * Paso de la saga en el que va una transferencia, segun lo que cuentas ya
 * aplico sobre los saldos. Es lo que permite ignorar mensajes reentregados.
 *
 * PENDIENTE -> DEBITO_OK -> COMPLETADA
 * PENDIENTE -> FALLIDA   (el debito falla, no hay nada que compensar)
 * DEBITO_OK -> REVERTIDA (el credito falla, se compensa el debito)
 */
public enum EstadoSaga {
    PENDIENTE,
    DEBITO_OK,
    COMPLETADA,
    FALLIDA,
    REVERTIDA
}
