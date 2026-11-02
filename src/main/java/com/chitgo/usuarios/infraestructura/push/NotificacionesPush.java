package com.chitgo.usuarios.infraestructura.push;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.pedidos.infraestructura.websocket.AvisoParaPush;
import com.chitgo.usuarios.aplicacion.DestinatariosPushService;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.PasarelaPush;
import com.chitgo.usuarios.dominio.puertos.SuscripcionesPush;

/** Un fallo del proveedor Push nunca deshace el pedido ni el aviso que ya se confirmó. */
@Component
class NotificacionesPush {
    private static final Logger LOG = LoggerFactory.getLogger(NotificacionesPush.class);
    private final DestinatariosPushService destinatarios;
    private final SuscripcionesPush suscripciones;
    private final PasarelaPush pasarela;
    private final ObjectMapper json;

    NotificacionesPush(DestinatariosPushService destinatarios, SuscripcionesPush suscripciones,
            PasarelaPush pasarela, ObjectMapper json) {
        this.destinatarios = destinatarios;
        this.suscripciones = suscripciones;
        this.pasarela = pasarela;
        this.json = json;
    }

    @Async("enviosPush")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(AvisoParaPush aviso) {
        if (!pasarela.habilitada()) {
            return;
        }
        try {
            String mensaje = json.writeValueAsString(Map.of("titulo", "Chit&Go", "evento", aviso.evento()));
            for (SuscripcionPush suscripcion : destinatarios.para(aviso.localId(), aviso.evento())) {
                enviarUna(suscripcion, mensaje);
            }
        } catch (Exception e) {
            LOG.warn("No se pudo preparar una notificación Push ({}).", e.getClass().getSimpleName());
        }
    }

    private void enviarUna(SuscripcionPush suscripcion, String mensaje) {
        try {
            int estado = pasarela.enviar(suscripcion, mensaje);
            if (estado == 404 || estado == 410) {
                suscripciones.eliminar(suscripcion.getEndpointHash(), suscripcion.getUsuarioId(), suscripcion.getLocalId());
            } else if (estado < 200 || estado >= 300) {
                LOG.warn("El proveedor Push respondió {}.", estado);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Los endpoints son secretos de la suscripción: no se incluyen ni en logs ni en auditoría.
            LOG.warn("Falló el envío de una notificación Push ({}).", e.getClass().getSimpleName());
        }
    }
}
