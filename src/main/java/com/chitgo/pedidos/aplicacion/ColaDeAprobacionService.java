package com.chitgo.pedidos.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.pedidos.aplicacion.dto.ItemPorAprobarDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoPorAprobarDto;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar.PedidoPorAprobar;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-07: la cola de aprobación de Caja (RF-14) con la alerta de los 3 minutos (RF-16). */
@Service
public class ColaDeAprobacionService {

    /** Más de esto sin resolverse es demorado: a los 180 segundos todavía no, a los 181 sí (CP-20-01). */
    static final long SEGUNDOS_HASTA_DEMORA = 180;

    private static final Comparator<PedidoPorAprobar> POR_LLEGADA =
            Comparator.comparing(PedidoPorAprobar::enviadoEn).thenComparing(PedidoPorAprobar::id);

    private final PedidosPorAprobar pedidosPorAprobar;
    private final Reloj reloj;

    public ColaDeAprobacionService(PedidosPorAprobar pedidosPorAprobar, Reloj reloj) {
        this.pedidosPorAprobar = pedidosPorAprobar;
        this.reloj = reloj;
    }

    /** Los pendientes del local, del más viejo al más nuevo. Por ahora la cola es solo de PENDIENTE_APROBACION. */
    @Transactional(readOnly = true)
    public List<PedidoPorAprobarDto> obtener(UsuarioAutenticado usuario, String estado) {
        if (!EstadoPedido.PENDIENTE_APROBACION.name().equals(estado)) {
            throw new SolicitudInvalidaException("La cola de Caja solo se pide con estado PENDIENTE_APROBACION");
        }
        Long localId = localDe(usuario);
        Instant ahora = reloj.ahora();
        return pedidosPorAprobar.pendientesDelLocal(localId).stream()
                .sorted(POR_LLEGADA)
                .map(p -> aDto(p, ahora))
                .toList();
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }

    private static PedidoPorAprobarDto aDto(PedidoPorAprobar p, Instant ahora) {
        // demorado sale de los mismos segundos que se muestran, así los dos campos nunca se contradicen.
        long segundos = Math.max(0, Duration.between(p.enviadoEn(), ahora).getSeconds());
        return new PedidoPorAprobarDto(p.id(), p.mesa(), p.enviadoEn(), segundos, segundos > SEGUNDOS_HASTA_DEMORA,
                p.items().stream()
                        .map(i -> new ItemPorAprobarDto(i.platoId(), i.plato(), i.cantidad(), i.observacion(),
                                i.disponible()))
                        .toList());
    }
}
