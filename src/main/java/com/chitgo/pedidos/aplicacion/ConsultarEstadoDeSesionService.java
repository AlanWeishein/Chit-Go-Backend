package com.chitgo.pedidos.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.pedidos.aplicacion.dto.EstadoDeSesionDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;

/** Estado completo y actual de la cuenta y sus pedidos, para reconectar sin repetir eventos (RNF-08). */
@Service
public class ConsultarEstadoDeSesionService {

    private final ClienteSesionRepository sesiones;
    private final PedidoRepository pedidos;
    private final ArmadorDePedidos armador;

    public ConsultarEstadoDeSesionService(ClienteSesionRepository sesiones, PedidoRepository pedidos,
            ArmadorDePedidos armador) {
        this.sesiones = sesiones;
        this.pedidos = pedidos;
        this.armador = armador;
    }

    @Transactional(readOnly = true)
    public EstadoDeSesionDto consultar(String tokenSesion) {
        ClienteSesion sesion = sesiones.buscarPorToken(tokenSesion == null ? "" : tokenSesion)
                .orElseThrow(() -> new AccesoDenegadoException("La sesión no es válida"));
        Cuenta cuenta = sesion.getCuenta();
        List<Pedido> deLaCuenta = pedidos.listarDeLaCuenta(cuenta.getId());
        Map<Long, String> nombres = armador.nombresDePlatos(sesion.getLocalId(), deLaCuenta);
        List<PedidoDto> dtos = deLaCuenta.stream().map(p -> armador.aDto(p, nombres)).toList();
        // El total suma los pedidos que no están Rechazados ni Cancelados.
        BigDecimal total = deLaCuenta.stream()
                .filter(p -> p.getEstado() != EstadoPedido.RECHAZADO && p.getEstado() != EstadoPedido.CANCELADO)
                .map(Pedido::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EstadoDeSesionDto(sesion.isEsIniciador(), cuenta.getEstado().name(), total, dtos);
    }
}
