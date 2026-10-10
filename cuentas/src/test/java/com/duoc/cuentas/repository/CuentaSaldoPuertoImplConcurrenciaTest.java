package com.duoc.cuentas.repository;

import com.duoc.cuentas.repository.CuentaRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de concurrencia real (sin mocks, sin contexto de Spring) de
 * CuentaSaldoPuertoImpl: 100 hilos debitando $1 cada uno, en paralelo, de
 * la misma cuenta con saldo suficiente. El saldo final debe quedar exacto
 * (saldo inicial - 100); antes de sincronizar debitar()/acreditar() esta
 * misma prueba perdia debitos por la race condition documentada en
 * evidencia/s7_saga_jms/logs_concurrencia_antes.txt.
 *
 * CuentaRepository se instancia a mano (no via Spring) y se llama su
 * cargarDatos() (normalmente @PostConstruct) directamente: es un POJO
 * simple que solo lee un CSV del classpath, no necesita el contenedor.
 */
class CuentaSaldoPuertoImplConcurrenciaTest {

    @Test
    void cienHilosDebitandoEnParalelo_saldoFinalDebeSerExacto() throws Exception {
        CuentaRepository cuentaRepository = new CuentaRepository();
        cuentaRepository.cargarDatos();

        CuentaSaldoPuertoImpl puerto = new CuentaSaldoPuertoImpl();
        setField(puerto, "cuentaRepository", cuentaRepository);

        Long cuentaId = 101L;
        BigDecimal saldoInicial = puerto.obtenerSaldo(cuentaId);

        int hilos = 100;
        // Pool de igual tamano que la cantidad de hilos: los 100 deben poder
        // arrancar a la vez para que el CountDownLatch los largue juntos de
        // verdad (con un pool mas chico, quedan en cola y se liberan en
        // tandas en vez de competir todos por el mismo lock al mismo tiempo).
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(hilos);

        for (int i = 0; i < hilos; i++) {
            executor.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                    puerto.debitar(cuentaId, BigDecimal.ONE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminados.countDown();
                }
            });
        }

        listos.await(5, TimeUnit.SECONDS);
        salida.countDown();
        assertTrue(terminados.await(10, TimeUnit.SECONDS), "los 100 hilos deberian terminar dentro del timeout");
        executor.shutdown();

        BigDecimal saldoFinal = puerto.obtenerSaldo(cuentaId);
        assertEquals(saldoInicial.subtract(BigDecimal.valueOf(hilos)), saldoFinal);
    }

    private void setField(Object objetivo, String nombreCampo, Object valor) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }
}
