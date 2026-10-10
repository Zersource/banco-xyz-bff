package com.duoc.cuentas.service;

import com.duoc.cuentas.model.Cuenta;
import com.duoc.cuentas.model.MovimientoAnual;
import com.duoc.cuentas.model.Transaccion;

import java.util.List;

/**
 * Servicio de negocio de cuentas: consultas de cuentas, movimientos y
 * transacciones, y las operaciones que modifican el saldo. Toda
 * modificacion de saldo pasa por CuentaSaldoPuerto (atomico).
 */
public interface CuentaService {

    List<Cuenta> obtenerTodasLasCuentas();

    Cuenta obtenerCuentaPorId(Long cuentaId);

    List<MovimientoAnual> obtenerTodosLosMovimientos();

    List<MovimientoAnual> obtenerMovimientosDeCuenta(Long cuentaId);

    List<Transaccion> obtenerTransaccionesGenerales();

    List<Transaccion> obtenerUltimasTransacciones(int cantidad);

    Double consultarSaldo(Long cuentaId);

    /**
     * Debita el monto de forma atomica (retiro del cajero).
     *
     * @return el saldo resultante.
     * @throws com.duoc.cuentas.exception.SaldoInsuficienteException si no alcanza el saldo.
     */
    Double debitar(Long cuentaId, Double monto);

    /**
     * Acredita el monto de forma atomica.
     *
     * @return el saldo resultante.
     */
    Double acreditar(Long cuentaId, Double monto);
}
