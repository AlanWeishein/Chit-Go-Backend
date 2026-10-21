package com.chitgo.pedidos.infraestructura.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/**
 * Adaptador de STOMP del puerto PublicadorDeEventos. Si hay una transacción en curso, el evento sale
 * después del commit: así nadie se entera de algo que después se deshace.
 */
@Component
public class PublicadorStomp implements PublicadorDeEventos {

    private final SimpMessagingTemplate plantilla;

    public PublicadorStomp(SimpMessagingTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public void aCaja(Long localId, EventoTiempoReal evento) {
        enviar(topic(localId, "caja/pedidos"), evento);
    }

    @Override
    public void aCocina(Long localId, EventoTiempoReal evento) {
        enviar(topic(localId, "cocina/cola"), evento);
    }

    @Override
    public void aMozos(Long localId, EventoTiempoReal evento) {
        enviar(topic(localId, "mozo/avisos"), evento);
    }

    @Override
    public void aAlertas(Long localId, EventoTiempoReal evento) {
        enviar(topic(localId, "alertas"), evento);
    }

    @Override
    public void aPantalla(Long localId, EventoTiempoReal evento) {
        enviar(topic(localId, "pantalla"), evento);
    }

    @Override
    public void aCliente(String tokenSesion, EventoTiempoReal evento) {
        despuesDelCommit(() -> plantilla.convertAndSendToUser(tokenSesion, "/queue/pedido", evento));
    }

    private static String topic(Long localId, String resto) {
        return "/topic/local/" + localId + "/" + resto;
    }

    private void enviar(String destino, EventoTiempoReal evento) {
        despuesDelCommit(() -> plantilla.convertAndSend(destino, evento));
    }

    private static void despuesDelCommit(Runnable envio) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }
}
