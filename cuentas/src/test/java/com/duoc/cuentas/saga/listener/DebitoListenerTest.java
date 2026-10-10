package com.duoc.cuentas.saga.listener;

import com.duoc.cuentas.exception.CuentaNoEncontradaException;
import com.duoc.cuentas.repository.CuentaSaldoPuerto;
import com.duoc.cuentas.saga.evento.EventoTransferencia;
import com.duoc.cuentas.saga.evento.TransferenciaEventoProductor;
import com.duoc.cuentas.saga.repository.EstadoSaga;
import com.duoc.cuentas.saga.repository.EstadoSagaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DebitoListenerTest {

    @Mock
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Mock
    private TransferenciaEventoProductor eventoProductor;

    private EstadoSagaRepository estadoSagaRepository;

    private DebitoListener debitoListener;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        estadoSagaRepository = new EstadoSagaRepository();
        debitoListener = new DebitoListener();
        setField(debitoListener, "cuentaSaldoPuerto", cuentaSaldoPuerto);
        setField(debitoListener, "estadoSagaRepository", estadoSagaRepository);
        setField(debitoListener, "eventoProductor", eventoProductor);
    }

    private void setField(Object objetivo, String nombreCampo, Object valor) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    private ConsumerRecord<String, EventoTransferencia> registro(String transaccionId, Long origen, Long destino, String monto) {
        EventoTransferencia evento = new EventoTransferencia(transaccionId, origen, destino, new BigDecimal(monto), null, null);
        return new ConsumerRecord<>("transferencia.iniciada", 0, 0L, transaccionId, evento);
    }

    @Test
    void debitoConSaldoSuficiente_deberiaPublicarDebitoRealizado() {
        when(cuentaSaldoPuerto.debitar(1L, new BigDecimal("100.00"))).thenReturn(true);

        debitoListener.manejarTransferenciaIniciada(registro("tx-1", 1L, 2L, "100.00"));

        verify(cuentaSaldoPuerto).debitar(1L, new BigDecimal("100.00"));
        verify(eventoProductor).publicarDebitoRealizado(any());
        assertEquals(EstadoSaga.DEBITO_OK, estadoSagaRepository.buscar("tx-1").orElseThrow());
    }

    @Test
    void debitoConFondosInsuficientes_deberiaPublicarDebitoFallidoYNoTocarSaldo() {
        when(cuentaSaldoPuerto.debitar(1L, new BigDecimal("999.00"))).thenReturn(false);

        debitoListener.manejarTransferenciaIniciada(registro("tx-2", 1L, 2L, "999.00"));

        verify(eventoProductor).publicarDebitoFallido(any());
        verify(eventoProductor, never()).publicarDebitoRealizado(any());
        assertEquals(EstadoSaga.FALLIDA, estadoSagaRepository.buscar("tx-2").orElseThrow());
    }

    @Test
    void origenInexistente_deberiaPublicarDebitoFallidoSinPropagarExcepcion() {
        when(cuentaSaldoPuerto.debitar(999L, new BigDecimal("50.00")))
                .thenThrow(new CuentaNoEncontradaException(999L));

        debitoListener.manejarTransferenciaIniciada(registro("tx-3", 999L, 2L, "50.00"));

        verify(eventoProductor).publicarDebitoFallido(any());
        assertEquals(EstadoSaga.FALLIDA, estadoSagaRepository.buscar("tx-3").orElseThrow());
    }

    @Test
    void mensajeReentregado_transferenciaYaEnDebitoOk_seIgnoraSinDebitarDeNuevo() {
        estadoSagaRepository.registrarPendiente("tx-4");
        estadoSagaRepository.cambiar("tx-4", EstadoSaga.PENDIENTE, EstadoSaga.DEBITO_OK);

        debitoListener.manejarTransferenciaIniciada(registro("tx-4", 1L, 2L, "100.00"));

        verify(cuentaSaldoPuerto, never()).debitar(any(), any());
        verify(eventoProductor, never()).publicarDebitoRealizado(any());
        verify(eventoProductor, never()).publicarDebitoFallido(any());
        assertEquals(EstadoSaga.DEBITO_OK, estadoSagaRepository.buscar("tx-4").orElseThrow());
    }
}
