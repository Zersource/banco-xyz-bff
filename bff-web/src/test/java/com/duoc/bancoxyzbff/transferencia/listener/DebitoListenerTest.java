package com.duoc.bancoxyzbff.transferencia.listener;

import com.duoc.bancoxyzbff.transferencia.evento.EventoTransferencia;
import com.duoc.bancoxyzbff.transferencia.evento.TransferenciaEventoProductor;
import com.duoc.bancoxyzbff.transferencia.model.EstadoTransaccion;
import com.duoc.bancoxyzbff.transferencia.model.Transaccion;
import com.duoc.bancoxyzbff.transferencia.repository.CuentaSaldoPuerto;
import com.duoc.bancoxyzbff.transferencia.repository.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DebitoListenerTest {

    @Mock
    private CuentaSaldoPuerto cuentaSaldoPuerto;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private TransferenciaEventoProductor eventoProductor;

    private DebitoListener debitoListener;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        debitoListener = new DebitoListener();
        setField(debitoListener, "cuentaSaldoPuerto", cuentaSaldoPuerto);
        setField(debitoListener, "transaccionRepository", transaccionRepository);
        setField(debitoListener, "eventoProductor", eventoProductor);
    }

    private void setField(Object objetivo, String nombreCampo, Object valor) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    @Test
    void debitoConSaldoSuficiente_deberiaPublicarDebitoRealizado() {
        Transaccion transaccion = new Transaccion(1L, 2L, new BigDecimal("100.00"));
        transaccion.setId(1L);
        when(transaccionRepository.findById(1L)).thenReturn(Optional.of(transaccion));
        when(cuentaSaldoPuerto.obtenerSaldo(1L)).thenReturn(new BigDecimal("500.00"));

        EventoTransferencia evento = new EventoTransferencia(1L, 1L, 2L, new BigDecimal("100.00"), null, null);
        debitoListener.manejarTransferenciaIniciada(evento);

        verify(cuentaSaldoPuerto).actualizarSaldo(1L, new BigDecimal("400.00"));
        verify(eventoProductor).publicarDebitoRealizado(any());
        assertEquals(EstadoTransaccion.DEBITO_OK, transaccion.getEstado());
    }

    @Test
    void debitoConFondosInsuficientes_deberiaPublicarDebitoFallidoYNoTocarSaldo() {
        Transaccion transaccion = new Transaccion(1L, 2L, new BigDecimal("999.00"));
        transaccion.setId(2L);
        when(transaccionRepository.findById(2L)).thenReturn(Optional.of(transaccion));
        when(cuentaSaldoPuerto.obtenerSaldo(1L)).thenReturn(new BigDecimal("50.00"));

        EventoTransferencia evento = new EventoTransferencia(2L, 1L, 2L, new BigDecimal("999.00"), null, null);
        debitoListener.manejarTransferenciaIniciada(evento);

        verify(cuentaSaldoPuerto, never()).actualizarSaldo(any(), any());
        verify(eventoProductor).publicarDebitoFallido(any());
        assertEquals(EstadoTransaccion.FALLIDA, transaccion.getEstado());
    }
}
