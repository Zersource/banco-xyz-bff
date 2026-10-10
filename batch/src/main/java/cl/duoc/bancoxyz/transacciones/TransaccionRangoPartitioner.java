package cl.duoc.bancoxyz.transacciones;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Divide {@code transacciones.csv} en rangos de lineas, uno por particion,
 * para que cada {@code cargarTransaccionesWorkerStep} procese solo su
 * porcion del archivo (equivalente al "start" y "end" por particion que
 * describe la guia de la semana, adaptado a lineas de un CSV en vez de un
 * rango de IDs de base de datos).
 * <p>
 * Cada {@link ExecutionContext} generado lleva dos claves:
 * <ul>
 *     <li>{@value #CLAVE_LINEAS_A_SALTAR}: cuantas lineas saltar desde el
 *     inicio del archivo (encabezado + particiones anteriores) antes de
 *     empezar a leer esta particion.</li>
 *     <li>{@value #CLAVE_CANTIDAD_ITEMS}: cuantos items debe leer esta
 *     particion como maximo.</li>
 * </ul>
 * {@code transaccionParticionadoReader} (en {@code TransaccionesDiariasJobConfig})
 * lee ambas claves via {@code @StepScope} y las usa para configurar
 * {@code linesToSkip} y {@code maxItemCount} de su propio
 * {@link org.springframework.batch.item.file.FlatFileItemReader}, uno
 * independiente por particion — por eso ya no hace falta
 * {@code SynchronizedItemStreamReader}: no hay un reader compartido entre
 * hilos, cada particion tiene el suyo.
 */
@Component
public class TransaccionRangoPartitioner implements Partitioner {

    public static final String CLAVE_LINEAS_A_SALTAR = "lineasASaltar";
    public static final String CLAVE_CANTIDAD_ITEMS = "cantidadItems";

    @Value("${app.csv.transacciones:classpath:data/semana_1/transacciones.csv}")
    private Resource transaccionesCsv;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalDatos = contarLineasDeDatos();
        Map<String, ExecutionContext> particiones = new HashMap<>();

        int base = totalDatos / gridSize;
        int resto = totalDatos % gridSize;
        int lineasASaltar = 1; // encabezado: id,fecha,monto,tipo

        for (int i = 0; i < gridSize; i++) {
            // El resto se reparte entre las primeras particiones (una fila
            // extra cada una) para no dejar una ultima particion desbalanceada.
            int cantidad = base + (i < resto ? 1 : 0);

            ExecutionContext contexto = new ExecutionContext();
            contexto.putInt(CLAVE_LINEAS_A_SALTAR, lineasASaltar);
            contexto.putInt(CLAVE_CANTIDAD_ITEMS, cantidad);
            particiones.put("partition" + i, contexto);

            lineasASaltar += cantidad;
        }
        return particiones;
    }

    private int contarLineasDeDatos() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(transaccionesCsv.getInputStream(), StandardCharsets.UTF_8))) {
            long totalLineas = reader.lines().count();
            return (int) Math.max(0, totalLineas - 1); // resta el encabezado
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo contar las lineas de " + transaccionesCsv, e);
        }
    }
}
