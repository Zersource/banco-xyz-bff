package com.duoc.cuentas.saga.repository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registro en memoria del paso de saga de cada transferencia (transaccionId).
 * Es el guard de idempotencia de los listeners: cada cambio de estado es un
 * compare-and-set atomico, asi que si Kafka reentrega un mensaje (o dos
 * instancias de cuentas lo reciben) solo una aplica el cambio sobre el saldo.
 * Sin persistencia nueva: se pierde al reiniciar, igual que los saldos.
 */
@Repository
public class EstadoSagaRepository {

    private final ConcurrentMap<Long, EstadoSaga> estados = new ConcurrentHashMap<>();

    /**
     * Registra la transferencia como PENDIENTE.
     *
     * @return true si es la primera vez que se ve; false si ya estaba registrada.
     */
    public boolean registrarPendiente(Long transaccionId) {
        return estados.putIfAbsent(transaccionId, EstadoSaga.PENDIENTE) == null;
    }

    public Optional<EstadoSaga> buscar(Long transaccionId) {
        return Optional.ofNullable(estados.get(transaccionId));
    }

    /**
     * Cambia el estado solo si sigue siendo el esperado.
     *
     * @return true si el cambio se aplico; false si otro mensaje ya lo cambio.
     */
    public boolean cambiar(Long transaccionId, EstadoSaga esperado, EstadoSaga nuevo) {
        return estados.replace(transaccionId, esperado, nuevo);
    }
}
