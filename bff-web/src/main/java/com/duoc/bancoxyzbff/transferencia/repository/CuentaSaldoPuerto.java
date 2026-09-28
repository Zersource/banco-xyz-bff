package com.duoc.bancoxyzbff.transferencia.repository;

import java.math.BigDecimal;

/**
 * PUNTO DE INTEGRACION: no conozco el nombre real de tu entidad/repositorio
 * de cuentas (Cuenta, CuentaRepository, etc.) desde este chat, asi que la
 * saga habla con este puerto minimo en vez de asumirlo.
 *
 * TODO (Claude Code / integracion manual): implementar
 * CuentaSaldoPuertoImpl delegando a tu CuentaRepository real, por ejemplo:
 *
 *   @Autowired
 *   private CuentaRepository cuentaRepository;
 *
 *   public BigDecimal obtenerSaldo(Long cuentaId) {
 *       return cuentaRepository.findById(cuentaId)
 *           .orElseThrow(...)
 *           .getSaldo();
 *   }
 *
 *   public void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo) {
 *       Cuenta cuenta = cuentaRepository.findById(cuentaId).orElseThrow(...);
 *       cuenta.setSaldo(nuevoSaldo);
 *       cuentaRepository.save(cuenta);
 *   }
 */
public interface CuentaSaldoPuerto {

    BigDecimal obtenerSaldo(Long cuentaId);

    void actualizarSaldo(Long cuentaId, BigDecimal nuevoSaldo);
}
