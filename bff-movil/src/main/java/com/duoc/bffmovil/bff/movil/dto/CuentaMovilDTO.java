package com.duoc.bffmovil.bff.movil.dto;

import com.duoc.bffmovil.dto.ClienteDTO;
import com.duoc.bffmovil.dto.CuentaDTO;
import com.duoc.bffmovil.dto.TransaccionDTO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO de cuenta para el canal Movil: solo lo esencial (cuentaId, nombre,
 * saldo, tipo) mas las ultimas 3 transacciones simplificadas. Sin edad y
 * sin historial de movimientos, para que pese menos que la respuesta web.
 */
public class CuentaMovilDTO {

    private Long cuentaId;
    private String nombre;
    private Double saldo;
    private String tipo;
    private List<TransaccionMovilDTO> ultimasTransacciones;

    public CuentaMovilDTO(CuentaDTO cuenta, ClienteDTO cliente, List<TransaccionDTO> ultimasTransacciones) {
        this.cuentaId = cuenta.getCuentaId();
        this.nombre = cliente == null ? null : cliente.getNombre();
        this.saldo = cuenta.getSaldo();
        this.tipo = cuenta.getTipo();
        this.ultimasTransacciones = ultimasTransacciones.stream()
                .map(TransaccionMovilDTO::new)
                .collect(Collectors.toList());
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public Double getSaldo() {
        return saldo;
    }

    public String getTipo() {
        return tipo;
    }

    public List<TransaccionMovilDTO> getUltimasTransacciones() {
        return ultimasTransacciones;
    }
}
