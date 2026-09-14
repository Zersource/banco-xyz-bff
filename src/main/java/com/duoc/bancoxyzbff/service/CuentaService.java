package com.duoc.bancoxyzbff.service;

import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.MovimientoAnual;
import com.duoc.bancoxyzbff.model.Transaccion;

import java.util.List;

/**
 * Servicio central de datos, compartido por los 3 BFF.
 * No conoce el concepto de "canal" (web/movil/cajero): eso lo maneja
 * cada controller al armar su propio DTO. Aca solo vive la logica
 * de negocio comun (buscar cuenta, calcular saldo, procesar retiro).
 */
public interface CuentaService {

    List<Cuenta> obtenerTodasLasCuentas();

    Cuenta obtenerCuentaPorId(Long cuentaId);

    List<MovimientoAnual> obtenerMovimientosDeCuenta(Long cuentaId);

    List<Transaccion> obtenerTransaccionesGenerales();

    List<Transaccion> obtenerUltimasTransacciones(int cantidad);

    Double consultarSaldo(Long cuentaId);

    Double realizarRetiro(Long cuentaId, Double monto);
}
