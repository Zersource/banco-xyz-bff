package com.duoc.bancoxyzbff.bff.movil.dto;

import com.duoc.bancoxyzbff.model.Transaccion;

import java.time.LocalDate;

/**
 * DTO liviano de transaccion para el canal Movil: solo fecha, monto y tipo.
 * No incluye el id interno, ya que no aporta valor a la app movil.
 */
public class TransaccionMovilDTO {

    private LocalDate fecha;
    private Double monto;
    private String tipo;

    public TransaccionMovilDTO(Transaccion transaccion) {
        this.fecha = transaccion.getFecha();
        this.monto = transaccion.getMonto();
        this.tipo = transaccion.getTipo();
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Double getMonto() {
        return monto;
    }

    public String getTipo() {
        return tipo;
    }
}
