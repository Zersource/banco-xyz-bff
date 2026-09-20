package com.duoc.bancoxyzbff.bff.web.dto;

import com.duoc.bancoxyzbff.model.Cuenta;
import com.duoc.bancoxyzbff.model.MovimientoAnual;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO de cuenta para el canal Web: expone TODOS los datos disponibles
 * (nombre, saldo, edad, tipo) mas el historial completo de movimientos.
 * Este es el BFF con mayor volumen de datos, pensado para una interfaz
 * de escritorio sin restriccion de ancho de banda.
 */
public class CuentaWebDTO {

    private Long cuentaId;
    private String nombre;
    private Double saldo;
    private Integer edad;
    private String tipo;
    private List<MovimientoWebDTO> movimientos;

    public CuentaWebDTO(Cuenta cuenta, List<MovimientoAnual> movimientos) {
        this.cuentaId = cuenta.getCuentaId();
        this.nombre = cuenta.getNombre();
        this.saldo = cuenta.getSaldo();
        this.edad = cuenta.getEdad();
        this.tipo = cuenta.getTipo();
        this.movimientos = movimientos.stream()
                .map(MovimientoWebDTO::new)
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

    public Integer getEdad() {
        return edad;
    }

    public String getTipo() {
        return tipo;
    }

    public List<MovimientoWebDTO> getMovimientos() {
        return movimientos;
    }
}
