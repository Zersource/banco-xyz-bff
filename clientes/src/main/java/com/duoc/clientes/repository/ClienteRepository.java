package com.duoc.clientes.repository;

import com.duoc.clientes.model.Cliente;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repositorio en memoria de los clientes, cargado una sola vez desde
 * "intereses.csv" (cuenta_id, nombre, saldo, edad, tipo). Sin persistencia
 * nueva, igual que el resto del proyecto.
 */
@Repository
public class ClienteRepository {

    private static final String ARCHIVO = "data/semana_1/intereses.csv";

    private final Map<Long, Cliente> clientes = new LinkedHashMap<>();

    @PostConstruct
    public void cargarDatos() {
        List<String[]> filas = CsvUtil.leerFilas(ARCHIVO);

        for (String[] fila : filas) {
            Long cuentaId = Long.parseLong(fila[0].trim());
            String nombre = fila[1].trim();
            // Las columnas saldo (2) y tipo (4) son del servicio cuentas
            Integer edad = Integer.parseInt(fila[3].trim());

            clientes.put(cuentaId, new Cliente(cuentaId, nombre, edad));
        }
    }

    public List<Cliente> buscarTodos() {
        return List.copyOf(clientes.values());
    }

    public Optional<Cliente> buscarPorCuentaId(Long cuentaId) {
        return Optional.ofNullable(clientes.get(cuentaId));
    }
}
