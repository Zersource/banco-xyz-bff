package com.duoc.bffmovil.bff.movil.dto;

import com.duoc.bffmovil.dto.TransaccionDTO;

import java.time.LocalDate;

/**
 * DTO liviano de transaccion para el canal Movil: solo fecha, monto y tipo.
 * Igual al de Exp2 S4/S5, solo que ahora se construye desde el DTO que
 * llega por HTTP en vez del modelo interno de datos.
 */
public class TransaccionMovilDTO {

    private LocalDate fecha;
    private Double monto;
    private String tipo;

    public TransaccionMovilDTO(TransaccionDTO transaccion) {
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
