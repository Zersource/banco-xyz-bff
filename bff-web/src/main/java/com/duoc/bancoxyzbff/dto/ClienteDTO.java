package com.duoc.bancoxyzbff.dto;

/**
 * Espejo del modelo Cliente del microservicio clientes, usado solo para
 * deserializar GET /clientes/{cuentaId}.
 */
public class ClienteDTO {

    private Long cuentaId;
    private String nombre;
    private Integer edad;

    public ClienteDTO() {
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }
}
