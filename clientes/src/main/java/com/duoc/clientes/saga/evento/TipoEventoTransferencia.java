package com.duoc.clientes.saga.evento;

public enum TipoEventoTransferencia {
    TRANSFERENCIA_INICIADA,
    DEBITO_REALIZADO,
    DEBITO_FALLIDO,
    TRANSFERENCIA_COMPLETADA,
    CREDITO_FALLIDO,
    TRANSFERENCIA_REVERTIDA
}
