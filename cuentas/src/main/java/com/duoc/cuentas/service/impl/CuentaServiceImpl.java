package com.duoc.cuentas.service.impl;

import com.duoc.cuentas.exception.CuentaNoEncontradaException;
import com.duoc.cuentas.exception.SaldoInsuficienteException;
import com.duoc.cuentas.model.Cuenta;
import com.duoc.cuentas.model.MovimientoAnual;
import com.duoc.cuentas.model.Transaccion;
import com.duoc.cuentas.repository.CuentaRepository;
import com.duoc.cuentas.repository.CuentaSaldoPuerto;
import com.duoc.cuentas.repository.MovimientoRepository;
import com.duoc.cuentas.repository.TransaccionRepository;
import com.duoc.cuentas.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CuentaServiceImpl implements CuentaService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Override
    public List<Cuenta> obtenerTodasLasCuentas() {
        return cuentaRepository.buscarTodas();
    }

    @Override
    public Cuenta obtenerCuentaPorId(Long cuentaId) {
        return cuentaRepository.buscarPorId(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
    }

    @Override
    public List<MovimientoAnual> obtenerTodosLosMovimientos() {
        return movimientoRepository.buscarTodos();
    }

    @Override
    public List<MovimientoAnual> obtenerMovimientosDeCuenta(Long cuentaId) {
        // Se valida que la cuenta exista antes de buscar sus movimientos.
        obtenerCuentaPorId(cuentaId);
        return movimientoRepository.buscarPorCuentaId(cuentaId);
    }

    @Override
    public List<Transaccion> obtenerTransaccionesGenerales() {
        return transaccionRepository.buscarTodas();
    }

    @Override
    public List<Transaccion> obtenerUltimasTransacciones(int cantidad) {
        return transaccionRepository.buscarUltimas(cantidad);
    }

    @Override
    public Double consultarSaldo(Long cuentaId) {
        return cuentaSaldoPuerto.obtenerSaldo(cuentaId).doubleValue();
    }

    @Override
    public Double retirar(Long cuentaId, Double monto) {
        if (!cuentaSaldoPuerto.debitar(cuentaId, BigDecimal.valueOf(monto))) {
            throw new SaldoInsuficienteException(cuentaId);
        }
        // El saldo se lee despues de la operacion atomica: es informativo y puede
        // incluir otra operacion concurrente; el descuento en si ya quedo exacto.
        return consultarSaldo(cuentaId);
    }
}
