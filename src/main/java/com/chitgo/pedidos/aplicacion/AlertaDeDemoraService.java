package com.chitgo.pedidos.aplicacion;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.dominio.Demora;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados.PedidoPendiente;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/**
 * HU-08: el aviso en vivo ALERTA_DEMORA a Caja cuando un pedido pasa los 3 minutos sin resolverse (RF-16).
 * Cada pasada avisa lo que se volvió demorado desde la anterior, con la misma regla que el GET de la cola.
 * La hora de la pasada anterior vive en memoria (ST-10, 29/10): si el back se reinicia justo cuando un pedido
 * cruza los 3 minutos, ese aviso se pierde, pero el pedido sigue saliendo {@code demorado} en el GET.
 */
@Service
public class AlertaDeDemoraService {

    private final PedidosDemorados pedidosDemorados;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    /** null hasta la primera pasada. */
    private Instant pasadaAnterior;

    public AlertaDeDemoraService(PedidosDemorados pedidosDemorados, PublicadorDeEventos eventos, Reloj reloj) {
        this.pedidosDemorados = pedidosDemorados;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Avisa los pendientes con {@code limiteDeEnvio(anterior) < enviadoEn <= limiteDeEnvio(ahora)}: las ventanas
     * de dos pasadas seguidas se tocan sin pisarse, así ningún pedido queda afuera ni recibe dos avisos. La
     * primera pasada solo anota la hora, para no avisar de golpe lo que ya estaba demorado. La transacción hace
     * que PublicadorStomp publique después del commit.
     */
    @Transactional(readOnly = true)
    public void revisarDemorados() {
        Instant ahora = reloj.ahora();
        if (pasadaAnterior == null) {
            pasadaAnterior = ahora;
            return;
        }
        if (!ahora.isAfter(pasadaAnterior)) {
            return; // el reloj no avanzó (o volvió para atrás): achicar la ventana repetiría avisos
        }
        // Si la consulta falla, la excepción corta acá y la hora no avanza: la pasada siguiente mira esta ventana.
        for (PedidoPendiente p : pedidosDemorados.pendientesEnviadosEntre(
                Demora.limiteDeEnvio(pasadaAnterior), Demora.limiteDeEnvio(ahora))) {
            eventos.aCaja(p.localId(), EventoTiempoReal.de(TipoEvento.ALERTA_DEMORA, p.id(), p.mesaId(),
                    EstadoPedido.PENDIENTE_APROBACION.name(), null, ahora));
        }
        pasadaAnterior = ahora;
    }
}
