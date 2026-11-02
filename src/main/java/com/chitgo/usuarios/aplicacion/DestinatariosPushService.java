package com.chitgo.usuarios.aplicacion;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.AsignacionesDeMozo;
import com.chitgo.usuarios.dominio.puertos.SuscripcionesPush;

/** Obtiene un snapshot en una transacción corta; el envío por red ocurre después, sin ocupar la conexión SQL. */
@Service
public class DestinatariosPushService {
    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final AsignacionesDeMozo asignaciones;
    private final SuscripcionesPush suscripciones;

    public DestinatariosPushService(CuentaRepository cuentas, PedidoRepository pedidos,
            AsignacionesDeMozo asignaciones, SuscripcionesPush suscripciones) {
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.asignaciones = asignaciones;
        this.suscripciones = suscripciones;
    }

    @Transactional(readOnly = true)
    public List<SuscripcionPush> para(Long localId, EventoTiempoReal evento) {
        Cuenta cuenta;
        if (evento.tipo() == TipoEvento.PEDIDO_LISTO) {
            Pedido pedido = pedidos.buscarPorId(evento.pedidoId(), localId).orElse(null);
            if (pedido == null || pedido.getEstado() != EstadoPedido.LISTO) {
                return List.of();
            }
            cuenta = pedido.getCuenta();
        } else if (evento.tipo() == TipoEvento.AVISO_CREADO && evento.cuentaId() != null) {
            cuenta = cuentas.buscarPorId(evento.cuentaId(), localId).orElse(null);
        } else {
            return List.of();
        }
        if (cuenta == null || !cuenta.aceptaPedidos() || cuenta.getMozoId() == null) {
            return List.of();
        }
        return asignaciones.usuarioDelEmpleado(cuenta.getMozoId(), localId)
                .filter(id -> asignaciones.mesasDe(id, localId).contains(cuenta.getMesa().getId()))
                .map(id -> suscripciones.delUsuario(id, localId)).orElseGet(List::of);
    }
}
