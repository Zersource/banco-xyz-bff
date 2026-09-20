package com.duoc.bffmovil.dto;

/**
 * Espejo del modelo Cuenta de bff-web, usado solo para deserializar la
 * respuesta de GET /interno/cuentas/{id}. bff-movil no accede a los
 * datos directamente, asi que no comparte la clase original (cada
 * microservicio es independiente en su propio jar).
 */
public class CuentaDTO {

    private Long cuentaId;
    private String nombre;
    private Double saldo;
    private Integer edad;
    private String tipo;

    public CuentaDTO() {
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

    public Double getSaldo() {
        return saldo;
    }

    public void setSaldo(Double saldo) {
        this.saldo = saldo;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
