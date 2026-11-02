package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.*;
import com.chitgo.pedidos.aplicacion.dto.*;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.AsignacionesDeMozo;

/** HU-05: el salón completo se consulta; las acciones requieren una mesa asignada al mozo. */
@Service
public class MozoService {
    private final MesaRepository mesas;
    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final AvisoRepository avisos;
    private final AsignacionesDeMozo asignaciones;
    private final ArmadorDePedidos armador;
    private final PublicadorDeEventos eventos;
    private final Auditor auditor;
    private final Reloj reloj;

    public MozoService(MesaRepository mesas, CuentaRepository cuentas, PedidoRepository pedidos,
            AvisoRepository avisos, AsignacionesDeMozo asignaciones, ArmadorDePedidos armador,
            PublicadorDeEventos eventos, Auditor auditor, Reloj reloj) {
        this.mesas = mesas;
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.avisos = avisos;
        this.asignaciones = asignaciones;
        this.armador = armador;
        this.eventos = eventos;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<MesaDeMozoDto> mesas(UsuarioAutenticado usuario, boolean todas) {
        Long localId = localDe(usuario);
        Set<Long> propias = asignaciones.mesasDe(usuario.usuarioId(), localId);
        List<Mesa> visibles = mesas.listarDelLocal(localId).stream()
                .filter(m -> todas || propias.contains(m.getId())).toList();
        Set<Long> ids = visibles.stream().map(Mesa::getId).collect(Collectors.toSet());
        Map<Long, Cuenta> porMesa = cuentas.listarDelLocal(localId, List.of(EstadoCuenta.ABIERTA, EstadoCuenta.SOLICITADA))
                .stream().filter(c -> ids.contains(c.getMesa().getId()))
                .collect(Collectors.toMap(c -> c.getMesa().getId(), Function.identity()));
        List<Long> cuentaIds = porMesa.values().stream().map(Cuenta::getId).toList();
        List<Pedido> deLasMesas = cuentaIds.isEmpty() ? List.of() : pedidos.listarDeLasCuentas(cuentaIds);
        Map<Long, List<Pedido>> porCuenta = deLasMesas.stream().collect(Collectors.groupingBy(p -> p.getCuenta().getId()));
        Map<Long, String> nombres = armador.nombresDePlatos(localId, deLasMesas);
        return visibles.stream().map(m -> {
            Cuenta cuenta = porMesa.get(m.getId());
            return new MesaDeMozoDto(m.getId(), m.getNumero(), m.isInhabilitada(), propias.contains(m.getId()),
                    cuenta == null ? null : cuenta.getId(), cuenta == null ? "LIBRE" : cuenta.getEstado().name(),
                    cuenta == null ? List.of() : porCuenta.getOrDefault(cuenta.getId(), List.of()).stream()
                            .map(p -> armador.aDto(p, nombres)).toList());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AvisoDto> avisos(UsuarioAutenticado usuario, boolean pendientes) {
        Long localId = localDe(usuario);
        Set<Long> propias = asignaciones.mesasDe(usuario.usuarioId(), localId);
        Set<Long> abiertas = cuentas.listarDelLocal(localId, List.of(EstadoCuenta.ABIERTA, EstadoCuenta.SOLICITADA))
                .stream().map(Cuenta::getId).collect(Collectors.toSet());
        return avisos.listarDelLocal(localId, pendientes).stream()
                .filter(a -> propias.contains(a.getMesa().getId()))
                .filter(a -> !pendientes || abiertas.contains(a.getCuentaId()))
                .map(a -> new AvisoDto(a.getId(), a.getMesa().getId(), a.getMesa().getNumero(), a.getTipo().name(),
                        a.getCreadoEn(), a.getAtendidoPorUsuarioId(), a.getAtendidoEn())).toList();
    }

    @Transactional
    public void atender(UsuarioAutenticado usuario, Long avisoId) {
        Long localId = localDe(usuario);
        Aviso aviso = avisos.buscarPorId(avisoId, localId)
                .orElseThrow(() -> new NoEncontradoException("El aviso no existe"));
        exigirAsignada(usuario, aviso.getMesa().getId());
        Cuenta cuenta = cuentas.buscarNoCerradaDeMesa(aviso.getMesa().getId())
                .filter(c -> Objects.equals(c.getId(), aviso.getCuentaId()))
                .orElseThrow(() -> new ReglaDeNegocioException("La cuenta del aviso ya está cerrada"));
        Instant ahora = reloj.ahora();
        if (aviso.getAtendidoEn() != null) {
            throw new ReglaDeNegocioException("El aviso ya fue atendido");
        }
        cuentas.subirVersion(cuenta);
        aviso.atender(usuario.usuarioId(), ahora);
        avisos.guardar(aviso);
        auditor.registrar(localId, usuario.usuarioId(), "ATENDER_AVISO", "AVISO", aviso.getId(),
                Map.of("atendido", false), Map.of("atendido", true, "tipo", aviso.getTipo().name()));
        eventos.aMozos(localId, EventoTiempoReal.de(TipoEvento.AVISO_ATENDIDO, null, aviso.getMesa().getId(),
                null, aviso.getTipo().name(), ahora));
    }

    @Transactional
    public void servir(UsuarioAutenticado usuario, Long pedidoId) {
        Long localId = localDe(usuario);
        Pedido pedido = pedidos.buscarPorId(pedidoId, localId)
                .orElseThrow(() -> new NoEncontradoException("El pedido no existe"));
        Cuenta cuenta = pedido.getCuenta();
        exigirAsignada(usuario, cuenta.getMesa().getId());
        cuenta.exigirNoCerrada();
        Instant ahora = reloj.ahora();
        if (pedido.getEstado() != EstadoPedido.LISTO) {
            throw new ReglaDeNegocioException("Solo se puede servir un pedido listo");
        }
        // Mismo orden de locks que el cierre: cuenta, pedido, auditoría.
        cuentas.subirVersion(cuenta);
        pedido.servir(usuario.usuarioId(), ahora);
        pedidos.guardar(pedido);
        auditor.registrar(localId, usuario.usuarioId(), "SERVIR_PEDIDO", "PEDIDO", pedido.getId(),
                Map.of("estado", EstadoPedido.LISTO.name()), Map.of("estado", EstadoPedido.SERVIDO.name()));
        EventoTiempoReal evento = EventoTiempoReal.de(TipoEvento.SERVIDO, pedido.getId(), cuenta.getMesa().getId(),
                pedido.getEstado().name(), null, ahora);
        eventos.aCliente(pedido.getClienteSesion().getTokenSesion(), evento);
        eventos.aCocina(localId, evento);
        eventos.aPantalla(localId, evento);
        eventos.aMozos(localId, evento);
    }

    private void exigirAsignada(UsuarioAutenticado usuario, Long mesaId) {
        if (!asignaciones.mesasDe(usuario.usuarioId(), usuario.localId()).contains(mesaId)) {
            throw new AccesoDenegadoException("Esta mesa no está asignada a vos");
        }
    }

    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
