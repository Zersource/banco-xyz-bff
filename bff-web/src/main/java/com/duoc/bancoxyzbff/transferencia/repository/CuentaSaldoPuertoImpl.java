package com.duoc.bancoxyzbff.transferencia.repository;

import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;
import com.duoc.bancoxyzbff.model.Cuenta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * Conecta la saga con el CuentaRepository real del proyecto (en memoria,
 * cargado desde intereses.csv). Se convierte Double <-> BigDecimal porque
 * Cuenta.saldo se maneja como Double en el resto del proyecto, mientras
 * que la saga usa BigDecimal para las operaciones de monto.
 */
@Repository
public class CuentaSaldoPuertoImpl implements CuentaSaldoPuerto {

    @Autowired
    private com.duoc.bancoxyzbff.repository.CuentaRepository cuentaRepository;

    @Override
    public BigDecimal obtenerSaldo(Long cuentaId) {
        Cuenta cuenta = cuentaRepository.buscarPorId(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        return BigDecimal.valueOf(cuenta.getSaldo());
    }

    @Override
    public void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo) {
        cuentaRepository.buscarPorId(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
        cuentaRepository.actualizarSaldo(cuentaId, nuevoSaldo.doubleValue());
    }
}
