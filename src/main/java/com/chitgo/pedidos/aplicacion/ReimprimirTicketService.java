package com.chitgo.pedidos.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.TicketRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-10: Caja reimprime el ticket de una cuenta cerrada (RF-18). Se arma de nuevo con el mismo ArmadorDeTicket del
 * cierre, desde la cuenta, sus pedidos y la fila de ticket: el número, la hora y el total son los de esa fila.
 */
@Service
public class ReimprimirTicketService {

    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final TicketRepository tickets;
    private final ArmadorDeTicket armadorDeTicket;

    public ReimprimirTicketService(CuentaRepository cuentas, PedidoRepository pedidos, TicketRepository tickets,
            ArmadorDeTicket armadorDeTicket) {
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.tickets = tickets;
        this.armadorDeTicket = armadorDeTicket;
    }

    @Transactional(readOnly = true)
    public TicketDto reimprimir(UsuarioAutenticado usuario, Long cuentaId) {
        Long localId = localDe(usuario);
        Cuenta cuenta = cuentas.buscarPorId(cuentaId, localId)
                .orElseThrow(() -> new NoEncontradoException("La cuenta no existe"));
        if (cuenta.getEstado() != EstadoCuenta.CERRADA) {
            throw new NoEncontradoException("Esta cuenta todavía no tiene ticket");
        }
        // El cierre guarda la cuenta y el ticket en la misma transacción: una cerrada sin ticket es un dato roto.
        Ticket ticket = tickets.buscarDeLaCuenta(cuentaId)
                .orElseThrow(() -> new IllegalStateException("La cuenta está cerrada y no tiene ticket"));
        return armadorDeTicket.armar(ticket, cuenta, pedidos.listarDeLaCuenta(cuentaId));
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
