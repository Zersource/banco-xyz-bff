package com.duoc.bancoxyzbff.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales que un canal envia para obtener su token de acceso:
 * su nombre de canal y la llave que tiene asignada (la misma llave
 * estatica que en S4 se enviaba en cada request, ahora solo se usa
 * una vez para autenticarse y obtener el token).
 */
public class TokenRequestDTO {

    @NotBlank(message = "El canal es obligatorio")
    private String canal;

    @NotBlank(message = "La clave es obligatoria")
    private String clave;

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }
}
