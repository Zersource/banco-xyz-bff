package com.duoc.bancoxyzbff.repository;

import com.duoc.bancoxyzbff.model.Transaccion;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repositorio en memoria de las transacciones diarias generales del banco.
 * Carga "transacciones.csv" (id, fecha, monto, tipo).
 * Este archivo no tiene cuenta_id, por lo que las transacciones no se
 * pueden asociar a una cuenta especifica; se exponen como actividad
 * general del banco.
 */
@Repository
public class TransaccionRepository {

    private static final String ARCHIVO = "data/semana_1/transacciones.csv";

    private final List<Transaccion> transacciones = new ArrayList<>();

    @PostConstruct
    public void cargarDatos() {
        List<String[]> filas = CsvUtil.leerFilas(ARCHIVO);

        for (String[] fila : filas) {
            Long id = Long.parseLong(fila[0].trim());
            LocalDate fecha = LocalDate.parse(fila[1].trim());
            Double monto = Double.parseDouble(fila[2].trim());
            String tipo = fila[3].trim();

            transacciones.add(new Transaccion(id, fecha, monto, tipo));
        }
    }

    public List<Transaccion> buscarTodas() {
        return List.copyOf(transacciones);
    }

    /**
     * Devuelve las N transacciones mas recientes, usado por el BFF Movil
     * para no enviar el listado completo (payload liviano).
     */
    public List<Transaccion> buscarUltimas(int cantidad) {
        return transacciones.stream()
                .sorted(Comparator.comparing(Transaccion::getFecha).reversed())
                .limit(cantidad)
                .collect(Collectors.toList());
    }
}
