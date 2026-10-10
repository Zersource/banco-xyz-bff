package com.duoc.bffmovil.dto;

/**
 * Espejo del modelo Transaccion del microservicio cuentas, usado para
 * deserializar GET /transacciones/ultimas. Solo los campos que usa el canal
 * movil (la fecha se ignora al deserializar).
 */
public class TransaccionDTO {

    private Long id;
    private Double monto;
    private String tipo;

    public TransaccionDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getMonto() {
        return monto;
    }

    public void setMonto(Double monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
