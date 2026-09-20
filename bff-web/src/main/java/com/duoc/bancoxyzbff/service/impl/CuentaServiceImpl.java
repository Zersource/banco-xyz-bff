package com.duoc.bancoxyzbff.service.impl;

import com.duoc.bancoxyzbff.exception.CuentaNoEncontradaException;
import com.duoc.bancoxyzbff.exception.SaldoInsuficienteException;
import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.MovimientoAnual;
import com.duoc.bancoxyzbff.model.Transaccion;
import com.duoc.bancoxyzbff.repository.CuentaRepository;
import com.duoc.bancoxyzbff.repository.MovimientoRepository;
import com.duoc.bancoxyzbff.repository.TransaccionRepository;
import com.duoc.bancoxyzbff.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CuentaServiceImpl implements CuentaService {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

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
        return obtenerCuentaPorId(cuentaId).getSaldo();
    }

    @Override
    public Double realizarRetiro(Long cuentaId, Double monto) {
        Cuenta cuenta = obtenerCuentaPorId(cuentaId);

        if (cuenta.getSaldo() < monto) {
            throw new SaldoInsuficienteException(cuentaId);
        }

        Double nuevoSaldo = cuenta.getSaldo() - monto;
        cuentaRepository.actualizarSaldo(cuentaId, nuevoSaldo);
        return nuevoSaldo;
    }
}
