package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.MotivoRechazo;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-07: Caja aprueba o rechaza un pedido pendiente (RF-14 y RF-15).
 *
 * <p>No lleva {@code @Transactional}: usa dos transacciones a propósito, como EnviarPedidoService. Si dos cajas
 * resuelven a la vez, o el cliente cancela en el mismo momento, la segunda choca con el @Version al guardar y su
 * transacción queda deshecha. Recién entonces se abre otra, de lectura, para ver quién ganó y elegir el mensaje
 * del 409: "ya fue cancelado" o "ya fue resuelto".
 */
@Service
public class ResolverPedidoService {

    private final PedidoRepository pedidos;
    private final PublicadorDeEventos eventos;
    private final Auditor auditor;
    private final Reloj reloj;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public ResolverPedidoService(PedidoRepository pedidos, PublicadorDeEventos eventos, Auditor auditor, Reloj reloj,
            PlatformTransactionManager transacciones) {
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.auditor = auditor;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Pendiente a Aprobado: entra a la cola de cocina. */
    public void aprobar(UsuarioAutenticado usuario, Long pedidoId) {
        resolver(usuario, pedidoId, "APROBAR_PEDIDO", p -> p.aprobar(usuario.usuarioId(), reloj.ahora()));
    }

    /** Pendiente a Rechazado con el motivo: avisa al cliente y no llega a cocina. */
    public void rechazar(UsuarioAutenticado usuario, Long pedidoId, MotivoRechazo motivo) {
        resolver(usuario, pedidoId, "RECHAZAR_PEDIDO", p -> p.rechazar(motivo, usuario.usuarioId(), reloj.ahora()));
    }

    private void resolver(UsuarioAutenticado usuario, Long pedidoId, String accion, Consumer<Pedido> transicion) {
        Long localId = localDe(usuario);
        try {
            escritura.executeWithoutResult(estado -> {
                Pedido pedido = buscar(pedidoId, localId);
                EstadoPedido anterior = pedido.getEstado();
                transicion.accept(pedido); // 409 si ya no está pendiente, con el mensaje según cómo quedó
                pedidos.guardar(pedido); // 409 si otro lo cambió un instante antes (@Version)

                auditor.registrar(localId, usuario.usuarioId(), accion, "PEDIDO", pedido.getId(),
                        Map.of("estado", anterior.name()), valorNuevo(pedido));
                publicar(localId, pedido);
            });
        } catch (ConflictoDeConcurrenciaException choque) {
            throw lectura.execute(estado -> quienGano(pedidoId, localId, choque));
        }
    }

    /** Después del choque, el pedido como lo dejó el que ganó. */
    private ReglaDeNegocioException quienGano(Long pedidoId, Long localId, ConflictoDeConcurrenciaException choque) {
        Pedido pedido = buscar(pedidoId, localId);
        if (pedido.getEstado() == EstadoPedido.PENDIENTE_APROBACION) {
            // Un pendiente no se edita (MVP 2) ni lleva prioridad: si sigue pendiente, no hay un motivo mejor.
            return choque;
        }
        return new ReglaDeNegocioException(pedido.mensajeNoResoluble());
    }

    private Pedido buscar(Long pedidoId, Long localId) {
        return pedidos.buscarPorId(pedidoId, localId)
                .orElseThrow(() -> new NoEncontradoException("El pedido no existe"));
    }

    private static Map<String, String> valorNuevo(Pedido pedido) {
        if (pedido.getMotivoRechazo() == null) {
            return Map.of("estado", pedido.getEstado().name());
        }
        return Map.of("estado", pedido.getEstado().name(), "motivo", pedido.getMotivoRechazo().name());
    }

    /** Todas las cajas lo sacan de la cola, también la que lo resolvió (PEDIDO_RESUELTO, 21/10). */
    private void publicar(Long localId, Pedido pedido) {
        Instant ahora = reloj.ahora();
        Long mesaId = pedido.getCuenta().getMesa().getId();
        String estado = pedido.getEstado().name();
        String token = pedido.getClienteSesion().getTokenSesion();

        if (pedido.getEstado() == EstadoPedido.APROBADO) {
            eventos.aCocina(localId, EventoTiempoReal.de(TipoEvento.PEDIDO_APROBADO, pedido.getId(), mesaId, estado, null, ahora));
            EventoTiempoReal aprobado = EventoTiempoReal.de(TipoEvento.APROBADO, pedido.getId(), mesaId, estado, null, ahora);
            eventos.aCliente(token, aprobado);
            eventos.aPantalla(localId, aprobado);
        } else {
            eventos.aCliente(token, EventoTiempoReal.de(TipoEvento.RECHAZADO, pedido.getId(), mesaId, estado,
                    pedido.getMotivoRechazo().name(), ahora));
        }
        eventos.aCaja(localId, EventoTiempoReal.de(TipoEvento.PEDIDO_RESUELTO, pedido.getId(), mesaId, estado, null, ahora));
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
