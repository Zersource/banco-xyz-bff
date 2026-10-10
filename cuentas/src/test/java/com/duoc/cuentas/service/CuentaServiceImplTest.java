package com.duoc.cuentas.service;

import com.duoc.cuentas.exception.SaldoInsuficienteException;
import com.duoc.cuentas.repository.CuentaRepository;
import com.duoc.cuentas.repository.CuentaSaldoPuertoImpl;
import com.duoc.cuentas.service.impl.CuentaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El retiro del cajero ya no escribe el saldo por su cuenta: pasa por
 * CuentaSaldoPuerto.debitar (atomico). Sin Spring, con los objetos reales.
 */
class CuentaServiceImplTest {

    private CuentaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        CuentaRepository cuentaRepository = new CuentaRepository();
        cuentaRepository.cargarDatos();
        CuentaSaldoPuertoImpl puerto = new CuentaSaldoPuertoImpl();
        ReflectionTestUtils.setField(puerto, "cuentaRepository", cuentaRepository);
        servicio = new CuentaServiceImpl();
        ReflectionTestUtils.setField(servicio, "cuentaRepository", cuentaRepository);
        ReflectionTestUtils.setField(servicio, "cuentaSaldoPuerto", puerto);
    }

    @Test
    void debitarConSaldoInsuficiente_lanzaExcepcionYNoCambiaElSaldo() {
        Double antes = servicio.consultarSaldo(101L);
        assertThrows(SaldoInsuficienteException.class, () -> servicio.debitar(101L, antes + 1));
        assertEquals(antes, servicio.consultarSaldo(101L));
    }

    @Test
    void cienRetirosSimultaneos_saldoFinalExacto() throws Exception {
        Double antes = servicio.consultarSaldo(101L);
        int hilos = 100;
        ExecutorService executor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(hilos);
        for (int i = 0; i < hilos; i++) {
            executor.submit(() -> {
                try {
                    salida.await();
                    servicio.debitar(101L, 1.0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    terminados.countDown();
                }
            });
        }
        salida.countDown();
        assertTrue(terminados.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(antes - hilos, servicio.consultarSaldo(101L));
    }
}
