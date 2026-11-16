package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.ItemSolicitado;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeEnvio;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.PedidoDuplicadoException;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/**
 * HU-02: enviar el pedido. Solo la sesión iniciadora, con el precio del servidor, e idempotente por uuidCliente.
 *
 * <p>No lleva {@code @Transactional}: usa dos transacciones a propósito. Si dos envíos iguales llegan a la vez,
 * el segundo INSERT rompe el UNIQUE de uuid_cliente y esa transacción queda deshecha; recién entonces se abre
 * otra, de lectura, para devolverle al segundo el pedido que ganó.
 */
@Service
public class EnviarPedidoService {

    private final ClienteSesionRepository sesiones;
    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final CartaRepository carta;
    private final PublicadorDeEventos eventos;
    private final ArmadorDePedidos armador;
    private final Reloj reloj;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public EnviarPedidoService(ClienteSesionRepository sesiones, CuentaRepository cuentas, PedidoRepository pedidos,
            CartaRepository carta, PublicadorDeEventos eventos, ArmadorDePedidos armador, Reloj reloj,
            PlatformTransactionManager transacciones) {
        this.sesiones = sesiones;
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.carta = carta;
        this.eventos = eventos;
        this.armador = armador;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    public ResultadoDeEnvio enviar(String tokenSesion, UUID uuidCliente, List<ItemSolicitado> solicitados) {
        try {
            return escritura.execute(estado -> enviarEnTransaccion(tokenSesion, uuidCliente, solicitados));
        } catch (PedidoDuplicadoException e) {
            return lectura.execute(estado -> {
                ClienteSesion sesion = sesionDe(tokenSesion);
                Pedido ganador = pedidos.buscarPorUuidCliente(uuidCliente.toString()).orElseThrow(() -> e);
                return reintento(ganador, sesion);
            });
        }
    }

    private ResultadoDeEnvio enviarEnTransaccion(String tokenSesion, UUID uuidCliente, List<ItemSolicitado> solicitados) {
        ClienteSesion sesion = sesionDe(tokenSesion);
        // El servidor lo controla aunque la app no muestre el carrito (CP-01-03).
        if (!sesion.isEsIniciador()) {
            throw new AccesoDenegadoException("Solo quien abrió la cuenta puede hacer pedidos");
        }
        if (uuidCliente == null) {
            throw new SolicitudInvalidaException("Falta el identificador del pedido");
        }
        if (solicitados == null || solicitados.isEmpty()) {
            throw new SolicitudInvalidaException("El pedido no tiene platos");
        }

        Optional<Pedido> existente = pedidos.buscarPorUuidCliente(uuidCliente.toString());
        if (existente.isPresent()) {
            return reintento(existente.get(), sesion);
        }

        Cuenta cuenta = sesion.getCuenta();
        if (!cuenta.aceptaPedidos()) {
            throw new ReglaDeNegocioException("La cuenta de esta mesa ya se cerró");
        }

        Instant ahora = reloj.ahora();
        Long localId = sesion.getLocalId();
        Pedido pedido = new Pedido(localId, cuenta, sesion, uuidCliente.toString(), ahora);
        agregarItems(pedido, localId, solicitados);
        Pedido guardado = pedidos.guardar(pedido);

        Long mesaId = cuenta.getMesa().getId();
        // Con la cuenta solicitada se puede seguir pidiendo: vuelve a Abierta y se avisa a Caja (06/10).
        boolean estabaSolicitada = cuenta.getEstado() == EstadoCuenta.SOLICITADA;
        if (estabaSolicitada && cuenta.reabrirSiEstabaSolicitada()) {
            cuentas.guardar(cuenta);
            eventos.aCaja(localId, EventoTiempoReal.de(TipoEvento.CUENTA_REABIERTA, guardado.getId(), mesaId,
                    EstadoCuenta.ABIERTA.name(), null, ahora));
        }
        eventos.aCaja(localId, EventoTiempoReal.de(TipoEvento.PEDIDO_ENVIADO, guardado.getId(), mesaId,
                guardado.getEstado().name(), null, ahora));
        return new ResultadoDeEnvio(armador.aDto(guardado, armador.nombresDePlatos(localId, List.of(guardado))), true);
    }

    private ResultadoDeEnvio reintento(Pedido existente, ClienteSesion sesion) {
        if (!existente.getClienteSesion().getId().equals(sesion.getId())) {
            throw new AccesoDenegadoException("Ese pedido es de otra sesión");
        }
        return new ResultadoDeEnvio(
                armador.aDto(existente, armador.nombresDePlatos(sesion.getLocalId(), List.of(existente))), false);
    }

    private ClienteSesion sesionDe(String tokenSesion) {
        return sesiones.buscarPorToken(tokenSesion == null ? "" : tokenSesion)
                .orElseThrow(() -> new AccesoDenegadoException("La sesión no es válida"));
    }

    /** El precio y la disponibilidad los pone el servidor: nunca se confía en lo que manda la app. */
    private void agregarItems(Pedido pedido, Long localId, List<ItemSolicitado> solicitados) {
        List<Long> ids = solicitados.stream().map(ItemSolicitado::platoId).distinct().toList();
        Map<Long, Plato> platos = carta.buscarPorIds(localId, ids).stream()
                .collect(Collectors.toMap(Plato::getId, Function.identity()));
        for (ItemSolicitado s : solicitados) {
            Plato plato = platos.get(s.platoId());
            if (plato == null) {
                throw new NoEncontradoException("El plato " + s.platoId() + " no existe");
            }
            if (!plato.isActivo()) {
                throw new ReglaDeNegocioException(
                        "\"" + plato.getNombre() + "\" ya no está disponible. Sacalo del carrito para seguir.");
            }
            new ItemPedido(localId, pedido, plato.getId(), (short) s.cantidad(), plato.getPrecio(), s.observacion());
        }
    }
}
