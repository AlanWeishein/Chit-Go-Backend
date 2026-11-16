package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.aplicacion.dto.ItemDeCocinaDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoDeCocinaDto;
import com.chitgo.pedidos.aplicacion.dto.PedidosAprobadosDto;
import com.chitgo.pedidos.aplicacion.dto.VistaDeCocinaDto;
import com.chitgo.pedidos.dominio.ColaDeCocina;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-11: la cola del KDS y pasar cada pedido a En preparación y a Listo. */
@Service
public class ColaDeCocinaService {

    private final PedidoRepository pedidos;
    private final PublicadorDeEventos eventos;
    private final Auditor auditor;
    private final ArmadorDePedidos armador;
    private final Reloj reloj;

    public ColaDeCocinaService(PedidoRepository pedidos, PublicadorDeEventos eventos, Auditor auditor,
            ArmadorDePedidos armador, Reloj reloj) {
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.auditor = auditor;
        this.armador = armador;
        this.reloj = reloj;
    }

    /** Las tres columnas del KDS: la cola en orden (prioritarios primero y por llegada), lo que se prepara y lo listo. */
    @Transactional(readOnly = true)
    public VistaDeCocinaDto obtener(UsuarioAutenticado usuario) {
        Long localId = localDe(usuario);
        List<Pedido> activos = pedidos.listarActivosDeCocina(localId);
        Map<Long, String> nombres = armador.nombresDePlatos(localId, activos);

        return new VistaDeCocinaDto(
                sinEmpezar(activos, nombres),
                enPreparacion(activos, nombres),
                deEstado(activos, EstadoPedido.LISTO).stream()
                        .sorted(Comparator.comparing(Pedido::getListoEn))
                        .map(p -> aDto(p, nombres)).toList());
    }

    /**
     * ST-17: lo que Caja ve de la cocina (P-36), sin los listos. Es la misma consulta y el mismo orden que el KDS,
     * para que Caja y Cocina vean la cola igual.
     */
    @Transactional(readOnly = true)
    public PedidosAprobadosDto obtenerAprobados(UsuarioAutenticado usuario) {
        Long localId = localDe(usuario);
        List<Pedido> activos = pedidos.listarActivosDeCocina(localId);
        Map<Long, String> nombres = armador.nombresDePlatos(localId, activos);
        return new PedidosAprobadosDto(sinEmpezar(activos, nombres), enPreparacion(activos, nombres));
    }

    /** Aprobado a En preparación. */
    @Transactional
    public void iniciarPreparacion(UsuarioAutenticado usuario, Long pedidoId) {
        transicionar(usuario, pedidoId, p -> p.iniciarPreparacion(reloj.ahora()), "INICIAR_PREPARACION",
                TipoEvento.EN_PREPARACION);
    }

    /** En preparación a Listo: avisa al mozo, al cliente y a la pantalla pública. */
    @Transactional
    public void marcarListo(UsuarioAutenticado usuario, Long pedidoId) {
        transicionar(usuario, pedidoId, p -> p.marcarListo(reloj.ahora()), "MARCAR_PEDIDO_LISTO", TipoEvento.LISTO);
    }

    private void transicionar(UsuarioAutenticado usuario, Long pedidoId, Consumer<Pedido> accion,
            String nombreAccion, TipoEvento tipo) {
        Long localId = localDe(usuario);
        Pedido pedido = pedidos.buscarPorId(pedidoId, localId)
                .orElseThrow(() -> new NoEncontradoException("El pedido no existe"));

        EstadoPedido anterior = pedido.getEstado();
        accion.accept(pedido); // 409 si se saltea un estado o se vuelve atrás
        pedidos.guardar(pedido); // 409 si otro lo cambió un instante antes (@Version)

        auditor.registrar(localId, usuario.usuarioId(), nombreAccion, "PEDIDO", pedido.getId(),
                Map.of("estado", anterior.name()), Map.of("estado", pedido.getEstado().name()));
        publicar(localId, pedido, tipo);
    }

    private void publicar(Long localId, Pedido pedido, TipoEvento tipo) {
        Instant ahora = reloj.ahora();
        Long mesaId = pedido.getCuenta().getMesa().getId();
        EventoTiempoReal evento = EventoTiempoReal.de(tipo, pedido.getId(), mesaId, pedido.getEstado().name(), null, ahora);

        eventos.aCocina(localId, evento);
        eventos.aCaja(localId, evento); // ST-17: la P-36 de Caja pasa el pedido de "sin empezar" a "en preparación"
        eventos.aPantalla(localId, evento);
        eventos.aCliente(pedido.getClienteSesion().getTokenSesion(), evento);
        if (tipo == TipoEvento.LISTO) {
            eventos.aMozos(localId, EventoTiempoReal.de(TipoEvento.PEDIDO_LISTO, pedido.getId(), mesaId,
                    pedido.getEstado().name(), null, ahora));
        }
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la cocina. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }

    /** Los aprobados en el orden de la cola: prioritarios primero y después por llegada. */
    private static List<PedidoDeCocinaDto> sinEmpezar(List<Pedido> activos, Map<Long, String> nombres) {
        return ColaDeCocina.con(deEstado(activos, EstadoPedido.APROBADO)).enOrden().stream()
                .map(p -> aDto(p, nombres)).toList();
    }

    private static List<PedidoDeCocinaDto> enPreparacion(List<Pedido> activos, Map<Long, String> nombres) {
        return deEstado(activos, EstadoPedido.EN_PREPARACION).stream()
                .sorted(Comparator.comparing(Pedido::getEnPreparacionEn))
                .map(p -> aDto(p, nombres)).toList();
    }

    private static List<Pedido> deEstado(List<Pedido> todos, EstadoPedido estado) {
        return todos.stream().filter(p -> p.getEstado() == estado).toList();
    }

    private static PedidoDeCocinaDto aDto(Pedido p, Map<Long, String> nombres) {
        return new PedidoDeCocinaDto(p.getId(), p.getCuenta().getMesa().getNumero(), p.getEstado().name(),
                p.isPrioridadManual(), p.getResueltoEn(), p.getEnPreparacionEn(), p.getListoEn(),
                p.getItems().stream()
                        .map(i -> new ItemDeCocinaDto(nombres.get(i.getPlatoId()), i.getCantidad(), i.getObservacion()))
                        .toList());
    }

}
