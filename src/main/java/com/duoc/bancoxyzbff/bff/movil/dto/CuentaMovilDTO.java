package com.duoc.bancoxyzbff.bff.movil.dto;

import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.Transaccion;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO de cuenta para el canal Movil: solo lo esencial (cuentaId, saldo, tipo)
 * mas las ultimas transacciones simplificadas. NO incluye nombre ni edad,
 * a diferencia del BFF Web, porque la app movil no los necesita para
 * mostrar el resumen de cuenta y asi se reduce el volumen de datos.
 */
public class CuentaMovilDTO {

    private Long cuentaId;
    private Double saldo;
    private String tipo;
    private List<TransaccionMovilDTO> ultimosMovimientos;

    public CuentaMovilDTO(Cuenta cuenta, List<Transaccion> ultimasTransacciones) {
        this.cuentaId = cuenta.getCuentaId();
        this.saldo = cuenta.getSaldo();
        this.tipo = cuenta.getTipo();
        this.ultimosMovimientos = ultimasTransacciones.stream()
                .map(TransaccionMovilDTO::new)
                .collect(Collectors.toList());
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Double getSaldo() {
        return saldo;
    }

    public String getTipo() {
        return tipo;
    }

    public List<TransaccionMovilDTO> getUltimosMovimientos() {
        return ultimosMovimientos;
    }
}
