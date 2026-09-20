package com.duoc.bancoxyzbff.auth.dto;

public class TokenResponseDTO {

    private String token;
    private String canal;
    private long expiraEnMinutos;

    public TokenResponseDTO(String token, String canal, long expiraEnMinutos) {
        this.token = token;
        this.canal = canal;
        this.expiraEnMinutos = expiraEnMinutos;
    }

    public String getToken() {
        return token;
    }

    public String getCanal() {
        return canal;
    }

    public long getExpiraEnMinutos() {
        return expiraEnMinutos;
    }
}
