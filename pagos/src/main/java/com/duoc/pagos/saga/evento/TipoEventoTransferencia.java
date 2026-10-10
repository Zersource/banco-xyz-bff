package com.duoc.pagos.saga.evento;

/**
 * Tipo de evento publicado en la saga de transferencia. Se guarda dentro
 * del propio mensaje ademas de inferirse por la cola en la que viaja,
 * asi el listener puede loguear/auditar sin ambiguedad.
 */
public enum TipoEventoTransferencia {
    TRANSFERENCIA_INICIADA,
    DEBITO_REALIZADO,
    DEBITO_FALLIDO,
    TRANSFERENCIA_COMPLETADA,
    CREDITO_FALLIDO,
    TRANSFERENCIA_REVERTIDA
}
