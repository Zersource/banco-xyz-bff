package com.duoc.bancoxyzbff.transferencia.model;

/**
 * Estados posibles de una transferencia dentro de la saga coreografiada.
 * PENDIENTE -> DEBITO_OK -> COMPLETADA (camino feliz)
 * PENDIENTE -> FALLIDA (el débito falla, no hay nada que compensar)
 * DEBITO_OK -> REVERTIDA (el crédito falla, se compensa el débito)
 */
public enum EstadoTransaccion {
    PENDIENTE,
    DEBITO_OK,
    COMPLETADA,
    FALLIDA,
    REVERTIDA
}
