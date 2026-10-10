package com.duoc.pagos.service;

import com.duoc.pagos.dto.TransferenciaRequestDTO;
import com.duoc.pagos.dto.TransferenciaResponseDTO;
import com.duoc.pagos.saga.evento.TransferenciaEventoProductor;
import com.duoc.pagos.model.EstadoTransaccion;
import com.duoc.pagos.model.Transaccion;
import com.duoc.pagos.repository.TransaccionRepository;
import com.duoc.pagos.service.impl.TransferenciaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferenciaServiceImplTest {

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private TransferenciaEventoProductor eventoProductor;

    private TransferenciaServiceImpl transferenciaService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        transferenciaService = new TransferenciaServiceImpl();
        // Sin @InjectMocks (convencion del curso): inyeccion manual por reflexion
        setField(transferenciaService, "transaccionRepository", transaccionRepository);
        setField(transferenciaService, "eventoProductor", eventoProductor);
    }

    private void setField(Object objetivo, String nombreCampo, Object valor) throws Exception {
        Field campo = objetivo.getClass().getDeclaredField(nombreCampo);
        campo.setAccessible(true);
        campo.set(objetivo, valor);
    }

    @Test
    void iniciarTransferencia_deberiaGuardarPendienteYPublicarEventoInicial() {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setCuentaOrigenId(1L);
        request.setCuentaDestinoId(2L);
        request.setMonto(new BigDecimal("100.00"));

        when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocacion -> {
            Transaccion t = invocacion.getArgument(0);
            t.setId("tx-10");
            return t;
        });

        TransferenciaResponseDTO respuesta = transferenciaService.iniciarTransferencia(request);

        assertEquals("tx-10", respuesta.getTransaccionId());
        assertEquals(EstadoTransaccion.PENDIENTE, respuesta.getEstado());
        verify(eventoProductor).publicarTransferenciaIniciada(any());
    }

    @Test
    void consultarEstado_deberiaRetornarEstadoActualDeLaTransaccion() {
        Transaccion transaccion = new Transaccion(1L, 2L, new BigDecimal("50.00"));
        transaccion.setId("tx-5");
        transaccion.setEstado(EstadoTransaccion.COMPLETADA);

        when(transaccionRepository.findById("tx-5")).thenReturn(Optional.of(transaccion));

        TransferenciaResponseDTO respuesta = transferenciaService.consultarEstado("tx-5");

        assertEquals(EstadoTransaccion.COMPLETADA, respuesta.getEstado());
    }
}
