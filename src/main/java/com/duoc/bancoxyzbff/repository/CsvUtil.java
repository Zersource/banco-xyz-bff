package com.duoc.bancoxyzbff.repository;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad simple para leer archivos CSV desde resources/data.
 * No se uso una libreria externa (como OpenCSV) porque el formato
 * del dataset legacy es simple (sin comas dentro de los campos),
 * asi que un split basico es suficiente y evita agregar dependencias
 * que no son necesarias para el alcance de esta actividad.
 */
public class CsvUtil {

    private CsvUtil() {
    }

    /**
     * Lee un CSV desde el classpath y devuelve las filas como arreglos de String,
     * saltando la primera linea (encabezado).
     */
    public static List<String[]> leerFilas(String rutaClasspath) {
        List<String[]> filas = new ArrayList<>();

        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(new ClassPathResource(rutaClasspath).getInputStream(), StandardCharsets.UTF_8))) {

            String linea;
            boolean esEncabezado = true;

            while ((linea = lector.readLine()) != null) {
                if (esEncabezado) {
                    esEncabezado = false;
                    continue;
                }
                if (linea.isBlank()) {
                    continue;
                }
                filas.add(linea.split(","));
            }

        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo CSV: " + rutaClasspath, e);
        }

        return filas;
    }
}
