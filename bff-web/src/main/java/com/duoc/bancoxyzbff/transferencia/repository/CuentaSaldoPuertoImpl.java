package com.duoc.bancoxyzbff.transferencia.repository;

import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;
import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.repository.CuentaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * Conecta la saga con el CuentaRepository real del proyecto (en memoria,
 * cargado desde intereses.csv). Se convierte Double <-> BigDecimal porque
 * Cuenta.saldo se maneja como Double en el resto del proyecto, mientras
 * que la saga usa BigDecimal para las operaciones de monto.
 *
 * debitar/acreditar sincronizan sobre la propia instancia de Cuenta que
 * devuelve CuentaRepository (es el mismo objeto guardado en su mapa
 * interno, no una copia), asi leer el saldo y escribirlo quedan atomicos
 * por cuenta sin tener que modificar CuentaRepository (fuera del alcance
 * de este modulo). Esto solo protege las llamadas que pasan por este
 * puerto: CuentaServiceImpl.realizarRetiro() (usado por bff-cajero) sigue
 * escribiendo el saldo por su cuenta sin este lock — reportado aparte,
 * no se toco por estar fuera del modulo transferencia.
 */
@Repository
public class CuentaSaldoPuertoImpl implements CuentaSaldoPuerto {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Override
    public BigDecimal obtenerSaldo(Long cuentaId) {
        return BigDecimal.valueOf(buscarCuenta(cuentaId).getSaldo());
    }

    @Override
    public boolean debitar(Long cuentaId, BigDecimal monto) {
        validarMonto(monto);
        Cuenta cuenta = buscarCuenta(cuentaId);
        synchronized (cuenta) {
            BigDecimal saldoActual = BigDecimal.valueOf(cuenta.getSaldo());
            if (saldoActual.compareTo(monto) < 0) {
                return false;
            }
            cuentaRepository.actualizarSaldo(cuentaId, saldoActual.subtract(monto).doubleValue());
            return true;
        }
    }

    @Override
    public void acreditar(Long cuentaId, BigDecimal monto) {
        validarMonto(monto);
        Cuenta cuenta = buscarCuenta(cuentaId);
        synchronized (cuenta) {
            BigDecimal saldoActual = BigDecimal.valueOf(cuenta.getSaldo());
            cuentaRepository.actualizarSaldo(cuentaId, saldoActual.add(monto).doubleValue());
        }
    }

    private Cuenta buscarCuenta(Long cuentaId) {
        return cuentaRepository.buscarPorId(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
    }
}
