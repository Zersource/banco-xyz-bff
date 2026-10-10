package com.duoc.cuentas.repository;

import com.duoc.cuentas.exception.CuentaNoEncontradaException;

import java.math.BigDecimal;

/**
 * Puerto de saldos de cuentas: unica via para modificar un saldo (la usan el
 * retiro del cajero y la saga de transferencias). debitar/acreditar
 * son atomicos: validan y modifican el saldo en un solo paso, para evitar
 * la race condition de leer-y-escribir por separado bajo concurrencia
 * (ver evidencia/s7_saga_jms/logs_concurrencia_antes.txt).
 */
public interface CuentaSaldoPuerto {

    /**
     * @throws CuentaNoEncontradaException si la cuenta no existe.
     */
    BigDecimal obtenerSaldo(Long cuentaId);

    /**
     * Debita monto de la cuenta de forma atomica.
     *
     * @return true si el debito se aplico (saldo suficiente); false si no
     *         alcanzaba el saldo (no se modifica nada en ese caso).
     * @throws IllegalArgumentException si monto es null o <= 0.
     * @throws CuentaNoEncontradaException si la cuenta no existe.
     */
    boolean debitar(Long cuentaId, BigDecimal monto);

    /**
     * Acredita monto a la cuenta de forma atomica.
     *
     * @throws IllegalArgumentException si monto es null o <= 0.
     * @throws CuentaNoEncontradaException si la cuenta no existe.
     */
    void acreditar(Long cuentaId, BigDecimal monto);
}
