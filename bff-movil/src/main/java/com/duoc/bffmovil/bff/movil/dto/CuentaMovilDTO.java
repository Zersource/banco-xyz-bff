package com.duoc.bffmovil.bff.movil.dto;

import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO de cuenta para el canal Movil: solo lo esencial (cuentaId, saldo, tipo)
 * mas las ultimas transacciones simplificadas. Misma logica que en
 * Exp2 S4/S5, ahora armada con los datos que llegan de bff-web via HTTP.
 */
public class CuentaMovilDTO {

    private Long cuentaId;
    private Double saldo;
    private String tipo;
    private List<TransaccionMovilDTO> ultimosMovimientos;

    public CuentaMovilDTO(CuentaDTO cuenta, List<TransaccionDTO> ultimasTransacciones) {
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
