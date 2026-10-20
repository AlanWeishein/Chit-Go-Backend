package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.aplicacion.dto.PedidoDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/** HU-02: el cliente cancela su pedido mientras Caja no lo aprobó. Para corregirlo, cancela y manda otro. */
@Service
public class CancelarPedidoService {

    static final String YA_CAMBIO = "El pedido ya cambió de estado. Si necesitás cambiarlo, llamá al mozo.";

    private final ClienteSesionRepository sesiones;
    private final PedidoRepository pedidos;
    private final PublicadorDeEventos eventos;
    private final ArmadorDePedidos armador;
    private final Reloj reloj;

    public CancelarPedidoService(ClienteSesionRepository sesiones, PedidoRepository pedidos,
            PublicadorDeEventos eventos, ArmadorDePedidos armador, Reloj reloj) {
        this.sesiones = sesiones;
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.armador = armador;
        this.reloj = reloj;
    }

    @Transactional
    public PedidoDto cancelar(String tokenSesion, Long pedidoId) {
        ClienteSesion sesion = sesiones.buscarPorToken(tokenSesion == null ? "" : tokenSesion)
                .orElseThrow(() -> new AccesoDenegadoException("La sesión no es válida"));
        Pedido pedido = pedidos.buscarPorId(pedidoId, sesion.getLocalId())
                .orElseThrow(() -> new NoEncontradoException("El pedido no existe"));
        if (!pedido.getClienteSesion().getId().equals(sesion.getId())) {
            throw new AccesoDenegadoException("Ese pedido es de otra sesión");
        }

        Instant ahora = reloj.ahora();
        pedido.cancelarPorCliente(ahora); // 409 si ya no está pendiente
        try {
            // Cancelar y aprobar compiten por el mismo pedido: el @Version decide quién gana.
            pedidos.guardar(pedido);
        } catch (ConflictoDeConcurrenciaException e) {
            throw new ReglaDeNegocioException(YA_CAMBIO);
        }

        // El cliente no tiene usuario: no se audita, queda la hora en el propio pedido (HU-15).
        eventos.aCaja(sesion.getLocalId(), EventoTiempoReal.de(TipoEvento.PEDIDO_CANCELADO, pedido.getId(),
                pedido.getCuenta().getMesa().getId(), pedido.getEstado().name(), null, ahora));
        return armador.aDto(pedido, armador.nombresDePlatos(sesion.getLocalId(), List.of(pedido)));
    }
}
