package cl.duoc.bancoxyz.intereses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resultado del calculo de interes mensual para una cuenta, listo para
 * persistirse en {@code cuenta_interes_mensual}.
 */
public class CuentaInteresProcesada {

    private Long cuentaId;
    private String nombre;
    private String tipo;
    private Integer edad;
    private BigDecimal saldoInicial;
    private BigDecimal tasaInteres;
    private BigDecimal interesGenerado;
    private BigDecimal saldoFinal;
    private LocalDateTime fechaProceso;

    public CuentaInteresProcesada(Long cuentaId, String nombre, String tipo, Integer edad,
                                   BigDecimal saldoInicial, BigDecimal tasaInteres,
                                   BigDecimal interesGenerado, BigDecimal saldoFinal) {
        this.cuentaId = cuentaId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.edad = edad;
        this.saldoInicial = saldoInicial;
        this.tasaInteres = tasaInteres;
        this.interesGenerado = interesGenerado;
        this.saldoFinal = saldoFinal;
        this.fechaProceso = LocalDateTime.now();
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getEdad() {
        return edad;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public BigDecimal getTasaInteres() {
        return tasaInteres;
    }

    public BigDecimal getInteresGenerado() {
        return interesGenerado;
    }

    public BigDecimal getSaldoFinal() {
        return saldoFinal;
    }

    public LocalDateTime getFechaProceso() {
        return fechaProceso;
    }
}
