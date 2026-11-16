package com.chitgo.pedidos.infraestructura.websocket;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.chitgo.pedidos.dominio.*;

class PublicadorStompTest {
    private final SimpMessagingTemplate stomp = mock(SimpMessagingTemplate.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final PublicadorStomp publicador = new PublicadorStomp(stomp, publisher);
    private final EventoTiempoReal evento = EventoTiempoReal.de(TipoEvento.PEDIDO_LISTO, 5L, 1L, "LISTO", null, Instant.EPOCH);

    @BeforeEach
    void transaccion() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void limpiar() { TransactionSynchronizationManager.clear(); }

    @Test
    void losCambiosSoloSePublicanAlConfirmarLaTransaccion() {
        publicador.aMozos(1L, evento);
        verifyNoInteractions(stomp);
        verify(publisher).publishEvent(new AvisoParaPush(1L, evento));
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(stomp).convertAndSend("/topic/local/1/mozo/avisos", evento);
    }

    @Test
    void unLoteQueSeDeshaceNoPublicaCancelaciones() {
        publicador.aCocina(1L, evento);
        publicador.aCliente("cliente", evento);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(stomp, publisher);
    }

    @Test
    void servirOAtenderNoGeneraNuevosPush() {
        publicador.aMozos(1L, EventoTiempoReal.de(TipoEvento.SERVIDO, 5L, 1L, "SERVIDO", null, Instant.EPOCH));
        publicador.aMozos(1L, EventoTiempoReal.de(TipoEvento.AVISO_ATENDIDO, null, 1L, null, null, Instant.EPOCH));
        verifyNoInteractions(publisher);
    }
}
