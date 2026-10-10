package com.duoc.bancoxyzbff.transferencia.repository;

import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;

import java.math.BigDecimal;

/**
 * Puerto que usa la saga para hablar con las cuentas. Desde EFT los saldos
 * son del microservicio cuentas: CuentaSaldoPuertoImpl llama a su API
 * (debito/credito), que aplica la operacion de forma atomica. Este puerto
 * es transitorio: en la Fase B los pasos de debito y credito de la saga
 * pasan a vivir dentro de cuentas.
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
