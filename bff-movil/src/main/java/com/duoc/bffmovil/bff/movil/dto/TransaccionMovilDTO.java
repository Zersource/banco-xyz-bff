package com.duoc.bffmovil.bff.movil.dto;

import com.duoc.bffmovil.dto.TransaccionDTO;

/**
 * DTO liviano de transaccion para el canal Movil: solo id, monto y tipo.
 */
public class TransaccionMovilDTO {

    private Long id;
    private Double monto;
    private String tipo;

    public TransaccionMovilDTO(TransaccionDTO transaccion) {
        this.id = transaccion.getId();
        this.monto = transaccion.getMonto();
        this.tipo = transaccion.getTipo();
    }

    public Long getId() {
        return id;
    }

    public Double getMonto() {
        return monto;
    }

    public String getTipo() {
        return tipo;
    }
}
