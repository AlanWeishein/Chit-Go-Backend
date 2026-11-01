package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.*;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-08 y HU-09. La cancelación de un lote se confirma completa o se deshace completa. */
@Service
public class GestionDeColaService {
    private final PedidoRepository pedidos;
    private final CuentaRepository cuentas;
    private final PublicadorDeEventos eventos;
    private final Auditor auditor;
    private final Reloj reloj;

    public GestionDeColaService(PedidoRepository pedidos, CuentaRepository cuentas,
            PublicadorDeEventos eventos, Auditor auditor, Reloj reloj) {
        this.pedidos = pedidos;
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    @Transactional
    public void priorizar(UsuarioAutenticado usuario, Long pedidoId, boolean prioridad) {
        Long localId = localDe(usuario);
        Pedido pedido = buscar(pedidoId, localId);
        pedido.getCuenta().exigirNoCerrada();
        boolean anterior = pedido.isPrioridadManual();
        if (pedido.getEstado() != EstadoPedido.APROBADO) {
            throw new ReglaDeNegocioException("Solo se puede cambiar la prioridad de un pedido en la cola de cocina");
        }
        if (anterior == prioridad) {
            return; // PUT idempotente: no se audita ni se notifica algo que no cambió.
        }
        cuentas.subirVersion(pedido.getCuenta());
        pedido.cambiarPrioridad(prioridad);
        pedidos.guardar(pedido);
        auditor.registrar(localId, usuario.usuarioId(), "CAMBIAR_PRIORIDAD", "PEDIDO", pedidoId,
                Map.of("prioridadManual", anterior), Map.of("prioridadManual", prioridad));
        EventoTiempoReal evento = EventoTiempoReal.de(TipoEvento.PRIORIDAD_CAMBIADA, pedidoId,
                pedido.getCuenta().getMesa().getId(), pedido.getEstado().name(), Boolean.toString(prioridad), reloj.ahora());
        eventos.aCocina(localId, evento);
        eventos.aCaja(localId, evento);
    }

    @Transactional
    public void cancelar(UsuarioAutenticado usuario, List<Long> pedidoIds) {
        Long localId = localDe(usuario);
        if (pedidoIds == null || pedidoIds.isEmpty() || pedidoIds.size() > 100
                || pedidoIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(pedidoIds).size() != pedidoIds.size()) {
            throw new SolicitudInvalidaException("Elegí entre 1 y 100 pedidos distintos para cancelar");
        }
        List<Pedido> lote = new ArrayList<>();
        // Se validan todos antes de cambiar ninguno, incluso al invocar el servicio sin Spring.
        for (Long id : pedidoIds) {
            Pedido pedido = buscar(id, localId);
            pedido.getCuenta().exigirNoCerrada();
            if (!OrigenDeCancelacion.CAJA.permite(pedido.getEstado())) {
                throw new ReglaDeNegocioException("El pedido " + id + " ya no se puede cancelar");
            }
            lote.add(pedido);
        }
        lote.sort(Comparator.comparing((Pedido p) -> p.getCuenta().getId()).thenComparing(Pedido::getId));
        Map<Long, Cuenta> cuentasDelLote = new LinkedHashMap<>();
        lote.forEach(p -> cuentasDelLote.putIfAbsent(p.getCuenta().getId(), p.getCuenta()));
        cuentasDelLote.values().forEach(cuentas::subirVersion);
        Instant ahora = reloj.ahora();
        for (Pedido pedido : lote) {
            EstadoPedido anterior = pedido.getEstado();
            pedido.cancelar(OrigenDeCancelacion.CAJA, usuario.usuarioId(), ahora);
            pedidos.guardar(pedido);
            auditor.registrar(localId, usuario.usuarioId(), "CANCELAR_PEDIDO", "PEDIDO", pedido.getId(),
                    Map.of("estado", anterior.name()), Map.of("estado", pedido.getEstado().name(), "origen", "CAJA"));
            publicarCancelacion(localId, pedido, ahora);
        }
    }

    private void publicarCancelacion(Long localId, Pedido pedido, Instant ahora) {
        Long mesaId = pedido.getCuenta().getMesa().getId();
        EventoTiempoReal interno = EventoTiempoReal.de(TipoEvento.PEDIDO_CANCELADO, pedido.getId(), mesaId,
                pedido.getEstado().name(), null, ahora);
        eventos.aCaja(localId, interno);
        eventos.aCocina(localId, interno);
        eventos.aMozos(localId, interno);
        EventoTiempoReal cliente = EventoTiempoReal.de(TipoEvento.CANCELADO, pedido.getId(), mesaId,
                pedido.getEstado().name(), null, ahora);
        eventos.aCliente(pedido.getClienteSesion().getTokenSesion(), cliente);
        eventos.aPantalla(localId, cliente);
    }

    private Pedido buscar(Long id, Long localId) {
        return pedidos.buscarPorId(id, localId).orElseThrow(() -> new NoEncontradoException("El pedido no existe"));
    }

    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
