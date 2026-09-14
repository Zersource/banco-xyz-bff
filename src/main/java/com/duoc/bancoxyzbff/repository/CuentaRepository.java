package com.duoc.bancoxyzbff.repository;

import com.duoc.bancoxyzbff.model.Cuenta;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio en memoria de las cuentas del banco.
 * Los datos se cargan una sola vez al levantar la aplicacion, leyendo
 * "intereses.csv" (que en realidad contiene el maestro de cuentas).
 *
 * Se uso un Map en memoria en vez de una base de datos (H2/JPA) porque
 * la actividad pide "crear APIs para acceder a los datos", no persistir
 * ni procesar en batch como en Exp1. Agregar una base de datos aca
 * hubiera sido complejidad innecesaria para lo que pide la pauta.
 */
@Repository
public class CuentaRepository {

    private static final String ARCHIVO = "data/semana_1/intereses.csv";

    private final Map<Long, Cuenta> cuentas = new LinkedHashMap<>();

    @PostConstruct
    public void cargarDatos() {
        List<String[]> filas = CsvUtil.leerFilas(ARCHIVO);

        for (String[] fila : filas) {
            Long cuentaId = Long.parseLong(fila[0].trim());
            String nombre = fila[1].trim();
            Double saldo = Double.parseDouble(fila[2].trim());
            Integer edad = Integer.parseInt(fila[3].trim());
            String tipo = fila[4].trim();

            cuentas.put(cuentaId, new Cuenta(cuentaId, nombre, saldo, edad, tipo));
        }
    }

    public List<Cuenta> buscarTodas() {
        return List.copyOf(cuentas.values());
    }

    public Optional<Cuenta> buscarPorId(Long cuentaId) {
        return Optional.ofNullable(cuentas.get(cuentaId));
    }

    /**
     * Actualiza el saldo de una cuenta en memoria (usado por el BFF Cajero
     * al procesar un retiro). No persiste en disco: al reiniciar la app
     * el saldo vuelve al valor original del CSV.
     */
    public void actualizarSaldo(Long cuentaId, Double nuevoSaldo) {
        Cuenta cuenta = cuentas.get(cuentaId);
        if (cuenta != null) {
            cuenta.setSaldo(nuevoSaldo);
        }
    }
}
