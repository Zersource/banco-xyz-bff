package com.duoc.pagos.model;

/**
 * Estados de una transferencia vistos desde pagos. Los pasos intermedios
 * de la saga (debito aplicado, credito, compensacion) los lleva cuentas;
 * pagos solo conoce el inicio y el resultado final.
 * PENDIENTE -> COMPLETADA (camino feliz)
 * PENDIENTE -> FALLIDA (el débito falla, no hay nada que compensar)
 * PENDIENTE -> REVERTIDA (el crédito falla, cuentas compensa el débito)
 */
public enum EstadoTransaccion {
    PENDIENTE,
    COMPLETADA,
    FALLIDA,
    REVERTIDA
}
