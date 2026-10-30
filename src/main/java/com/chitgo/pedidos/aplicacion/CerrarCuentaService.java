package com.chitgo.pedidos.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.aplicacion.dto.PedidoSinServirDto;
import com.chitgo.pedidos.aplicacion.dto.PlatoSinServirDto;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.MotivoDeConfirmacion;
import com.chitgo.pedidos.dominio.OrigenDeCancelacion;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.pedidos.dominio.puertos.TicketRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-10: Caja cierra la cuenta, cancela lo que no se sirvió y emite el ticket (RF-17 y RF-18).
 *
 * <p>Es una sola transacción: la cuenta, los pedidos cancelados, el ticket y la auditoría se guardan juntos o no se
 * guarda nada. Si la cuenta o algún pedido chocan con el @Version (Cocina lo marca Listo en ese segundo, el mozo lo
 * sirve, el cliente manda otro pedido), el repositorio lanza ConflictoDeConcurrenciaException, sale 409 y Caja
 * reintenta. Los eventos salen después del commit (PublicadorStomp), así que un 409 no publica nada.
 *
 * <p>La cuenta se guarda primero: su UPDATE toma el lock de la fila antes que los pedidos y el INSERT del ticket
 * (cuya FK toma un lock compartido sobre la cuenta), así dos cierres, o un cierre y un envío, no se traban entre sí.
 */
@Service
public class CerrarCuentaService {

    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final TicketRepository tickets;
    private final ArmadorDePedidos armador;
    private final ArmadorDeTicket armadorDeTicket;
    private final PublicadorDeEventos eventos;
    private final Auditor auditor;
    private final Reloj reloj;

    public CerrarCuentaService(CuentaRepository cuentas, PedidoRepository pedidos, TicketRepository tickets,
            ArmadorDePedidos armador, ArmadorDeTicket armadorDeTicket, PublicadorDeEventos eventos, Auditor auditor,
            Reloj reloj) {
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.tickets = tickets;
        this.armador = armador;
        this.armadorDeTicket = armadorDeTicket;
        this.eventos = eventos;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    /**
     * @param confirmar        Caja ya vio la pregunta y quiere cerrar igual
     * @param pedidosACancelar los pedidos sin servir que Caja vio al confirmar; puede ser null. Solo sirve para
     *                         comprobar que no apareció uno nuevo: se cancelan los que están sin servir ahora
     * @throws ConfirmacionDeCierreRequeridaException (409) si hay motivos y no se confirmó, o si apareció un pedido
     *                                                sin servir que Caja no vio
     */
    @Transactional
    public TicketDto cerrar(UsuarioAutenticado usuario, Long cuentaId, boolean confirmar, List<Long> pedidosACancelar) {
        Long localId = localDe(usuario);
        Cuenta cuenta = cuentas.buscarPorId(cuentaId, localId)
                .orElseThrow(() -> new NoEncontradoException("La cuenta no existe"));
        cuenta.exigirNoCerrada(); // 409 antes de calcular motivos
        EstadoCuenta anterior = cuenta.getEstado();

        List<Pedido> deLaCuenta = pedidos.listarDeLaCuenta(cuentaId);
        List<Pedido> sinServir = deLaCuenta.stream().filter(Pedido::estaSinServir).toList();
        exigirConfirmacion(localId, anterior, sinServir, confirmar, pedidosACancelar);

        Instant ahora = reloj.ahora();
        for (Pedido pedido : sinServir) {
            pedido.cancelar(OrigenDeCancelacion.CIERRE_DE_CUENTA, usuario.usuarioId(), ahora);
        }
        BigDecimal total = totalDe(deLaCuenta);
        cuenta.cerrar(usuario.usuarioId(), total, ahora);

        // Primero la cuenta (toma el lock y choca con el cierre o el envío de otro), después los pedidos y al final el ticket.
        cuentas.guardar(cuenta);
        sinServir.forEach(pedidos::guardar);
        Ticket ticket = tickets.guardar(new Ticket(localId, cuenta, ahora, total));
        TicketDto dto = armadorDeTicket.armar(ticket, cuenta, deLaCuenta);

        // Un solo registro para todo el cierre, no uno por pedido (decidido el 29/10).
        auditor.registrar(localId, usuario.usuarioId(), "CERRAR_CUENTA", "CUENTA", cuenta.getId(),
                Map.of("estado", anterior.name()),
                Map.of("estado", EstadoCuenta.CERRADA.name(), "total", total, "ticketId", ticket.getId(),
                        "pedidosCancelados", sinServir.stream().map(Pedido::getId).toList()));
        publicar(localId, cuenta, sinServir, ahora);
        return dto;
    }

    /**
     * Nadie pidió la cuenta y pedidos sin servir son los dos motivos para frenar. Con confirmar, solo frena un pedido
     * sin servir que no estaba en la lista que vio Caja: lo que se sirvió o se canceló en el medio no frena nada.
     */
    private void exigirConfirmacion(Long localId, EstadoCuenta estado, List<Pedido> sinServir, boolean confirmar,
            List<Long> pedidosACancelar) {
        List<MotivoDeConfirmacion> motivos = new ArrayList<>();
        if (estado == EstadoCuenta.ABIERTA) {
            motivos.add(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA);
        }
        if (!sinServir.isEmpty()) {
            motivos.add(MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR);
        }
        if (motivos.isEmpty()) {
            return;
        }
        if (confirmar && vistosPorCaja(pedidosACancelar).containsAll(sinServir.stream().map(Pedido::getId).toList())) {
            return;
        }
        throw new ConfirmacionDeCierreRequeridaException(motivos, aDto(localId, sinServir));
    }

    private static Set<Long> vistosPorCaja(List<Long> pedidosACancelar) {
        Set<Long> vistos = new HashSet<>();
        if (pedidosACancelar != null) {
            pedidosACancelar.stream().filter(Objects::nonNull).forEach(vistos::add);
        }
        return vistos;
    }

    private List<PedidoSinServirDto> aDto(Long localId, List<Pedido> sinServir) {
        Map<Long, String> nombres = armador.nombresDePlatos(localId, sinServir);
        return sinServir.stream()
                .map(p -> new PedidoSinServirDto(p.getId(), p.getEstado().name(), p.getItems().stream()
                        .map(i -> new PlatoSinServirDto(nombres.get(i.getPlatoId()), i.getCantidad()))
                        .toList()))
                .toList();
    }

    /** Lo que no quedó Rechazado ni Cancelado, con el precio del momento del pedido (CP-22-03). Con todo cancelado, 0. */
    private static BigDecimal totalDe(List<Pedido> deLaCuenta) {
        return deLaCuenta.stream()
                .filter(Pedido::cuentaEnElTotal)
                .map(Pedido::total)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Cada pedido cancelado sale de cocina, de caja y de la pantalla; la cuenta, de caja y de los mozos (29/10). */
    private void publicar(Long localId, Cuenta cuenta, List<Pedido> cancelados, Instant ahora) {
        Long mesaId = cuenta.getMesa().getId();
        String cancelado = EstadoPedido.CANCELADO.name();
        for (Pedido pedido : cancelados) {
            EventoTiempoReal paraElPersonal = EventoTiempoReal.de(TipoEvento.PEDIDO_CANCELADO, pedido.getId(), mesaId,
                    cancelado, null, ahora);
            eventos.aCocina(localId, paraElPersonal);
            eventos.aCaja(localId, paraElPersonal);
            eventos.aPantalla(localId, paraElPersonal);
            eventos.aCliente(pedido.getClienteSesion().getTokenSesion(),
                    EventoTiempoReal.de(TipoEvento.CANCELADO, pedido.getId(), mesaId, cancelado, null, ahora));
        }
        EventoTiempoReal cerrada = EventoTiempoReal.deCuenta(TipoEvento.CUENTA_CERRADA, cuenta.getId(), mesaId,
                EstadoCuenta.CERRADA.name(), ahora);
        eventos.aCaja(localId, cerrada);
        eventos.aMozos(localId, cerrada);
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
