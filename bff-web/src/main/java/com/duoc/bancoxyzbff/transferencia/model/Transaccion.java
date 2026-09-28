package com.duoc.bancoxyzbff.transferencia.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa una transferencia entre dos cuentas.
 * Es el "registro de estado" de la saga: cada listener actualiza este
 * registro a medida que va avanzando (o compensando) la transacción.
 *
 * cuentaOrigenId/cuentaDestinoId referencian Cuenta.cuentaId (Long) del
 * modulo base; el saldo real de esa cuenta se maneja como Double (ver
 * CuentaSaldoPuertoImpl), con conversion a BigDecimal solo dentro de la
 * saga.
 */
@Entity
@Table(name = "transaccion")
public class Transaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_origen_id", nullable = false)
    private Long cuentaOrigenId;

    @Column(name = "cuenta_destino_id", nullable = false)
    private Long cuentaDestinoId;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransaccion estado;

    @Column(name = "motivo_fallo")
    private String motivoFallo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    public Transaccion() {
    }

    public Transaccion(Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto) {
        this.cuentaOrigenId = cuentaOrigenId;
        this.cuentaDestinoId = cuentaDestinoId;
        this.monto = monto;
        this.estado = EstadoTransaccion.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCuentaOrigenId() {
        return cuentaOrigenId;
    }

    public void setCuentaOrigenId(Long cuentaOrigenId) {
        this.cuentaOrigenId = cuentaOrigenId;
    }

    public Long getCuentaDestinoId() {
        return cuentaDestinoId;
    }

    public void setCuentaDestinoId(Long cuentaDestinoId) {
        this.cuentaDestinoId = cuentaDestinoId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoTransaccion getEstado() {
        return estado;
    }

    public void setEstado(EstadoTransaccion estado) {
        this.estado = estado;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public String getMotivoFallo() {
        return motivoFallo;
    }

    public void setMotivoFallo(String motivoFallo) {
        this.motivoFallo = motivoFallo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
