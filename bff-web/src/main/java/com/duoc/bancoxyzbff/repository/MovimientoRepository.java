package com.duoc.bancoxyzbff.repository;

import com.duoc.bancoxyzbff.model.MovimientoAnual;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repositorio en memoria del historial de movimientos por cuenta.
 * Carga "cuentas_anuales.csv" (cuenta_id, fecha, transaccion, monto, descripcion).
 */
@Repository
public class MovimientoRepository {

    private static final String ARCHIVO = "data/semana_1/cuentas_anuales.csv";

    private final List<MovimientoAnual> movimientos = new ArrayList<>();

    @PostConstruct
    public void cargarDatos() {
        List<String[]> filas = CsvUtil.leerFilas(ARCHIVO);

        for (String[] fila : filas) {
            Long cuentaId = Long.parseLong(fila[0].trim());
            LocalDate fecha = LocalDate.parse(fila[1].trim());
            String transaccion = fila[2].trim();
            Double monto = Double.parseDouble(fila[3].trim());
            String descripcion = fila[4].trim();

            movimientos.add(new MovimientoAnual(cuentaId, fecha, transaccion, monto, descripcion));
        }
    }

    public List<MovimientoAnual> buscarPorCuentaId(Long cuentaId) {
        return movimientos.stream()
                .filter(m -> m.getCuentaId().equals(cuentaId))
                .collect(Collectors.toList());
    }
}
