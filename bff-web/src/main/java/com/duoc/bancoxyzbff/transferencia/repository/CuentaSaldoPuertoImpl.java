package com.duoc.bancoxyzbff.transferencia.repository;

import com.duoc.bancoxyzbff.client.CuentasClient;
import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;

/**
 * Implementacion del puerto sobre el microservicio cuentas (via
 * CuentasClient, con token del canal web y Circuit Breaker). La atomicidad
 * la garantiza cuentas; aca solo se traducen las respuestas HTTP a lo que
 * espera la saga: 400 en el debito = saldo insuficiente (false), 404 =
 * cuenta no encontrada.
 */
@Repository
public class CuentaSaldoPuertoImpl implements CuentaSaldoPuerto {

    @Autowired
    private CuentasClient cuentasClient;

    @Override
    public BigDecimal obtenerSaldo(Long cuentaId) {
        try {
            return BigDecimal.valueOf(cuentasClient.consultarSaldo(cuentaId));
        } catch (HttpClientErrorException ex) {
            throw traducir(ex, cuentaId);
        }
    }

    @Override
    public boolean debitar(Long cuentaId, BigDecimal monto) {
        validarMonto(monto);
        try {
            cuentasClient.debitar(cuentaId, monto.doubleValue());
            return true;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                return false;
            }
            throw traducir(ex, cuentaId);
        }
    }

    @Override
    public void acreditar(Long cuentaId, BigDecimal monto) {
        validarMonto(monto);
        try {
            cuentasClient.acreditar(cuentaId, monto.doubleValue());
        } catch (HttpClientErrorException ex) {
            throw traducir(ex, cuentaId);
        }
    }

    private RuntimeException traducir(HttpClientErrorException ex, Long cuentaId) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new CuentaNoEncontradaException(cuentaId);
        }
        return ex;
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
    }
}
