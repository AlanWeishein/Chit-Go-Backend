package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.CancelarPedidoService;
import com.chitgo.pedidos.aplicacion.ConsultarEstadoDeSesionService;
import com.chitgo.pedidos.aplicacion.EnviarPedidoService;
import com.chitgo.pedidos.aplicacion.dto.EstadoDeSesionDto;
import com.chitgo.pedidos.aplicacion.dto.ItemSolicitado;
import com.chitgo.pedidos.aplicacion.dto.PedidoDto;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeEnvio;

/** HU-02. El cliente se identifica con el header X-Session-Token. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/public")
public class PedidoClienteController {

    private static final String TOKEN = "X-Session-Token";

    private final EnviarPedidoService enviarPedido;
    private final CancelarPedidoService cancelarPedido;
    private final ConsultarEstadoDeSesionService consultarEstado;

    public PedidoClienteController(EnviarPedidoService enviarPedido, CancelarPedidoService cancelarPedido,
            ConsultarEstadoDeSesionService consultarEstado) {
        this.enviarPedido = enviarPedido;
        this.cancelarPedido = cancelarPedido;
        this.consultarEstado = consultarEstado;
    }

    /** 201 si se creó, 200 si era un reintento y se devuelve el pedido que ya existía (RNF-09). */
    @PostMapping("/pedidos")
    public ResponseEntity<PedidoDto> enviar(@RequestHeader(value = TOKEN, required = false) String tokenSesion,
            @Valid @RequestBody EnviarPedidoRequest cuerpo) {
        List<ItemSolicitado> items = cuerpo.items().stream()
                .map(i -> new ItemSolicitado(i.platoId(), i.cantidad(), i.observacion()))
                .toList();
        ResultadoDeEnvio resultado = enviarPedido.enviar(tokenSesion, cuerpo.uuidCliente(), items);
        HttpStatus estado = resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(resultado.pedido());
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public PedidoDto cancelar(@RequestHeader(value = TOKEN, required = false) String tokenSesion,
            @PathVariable("id") Long id) {
        return cancelarPedido.cancelar(tokenSesion, id);
    }

    /** El estado completo y actual, para reconectar sin repetir eventos. */
    @GetMapping("/sesion/estado")
    public EstadoDeSesionDto estado(@RequestHeader(value = TOKEN, required = false) String tokenSesion) {
        return consultarEstado.consultar(tokenSesion);
    }
}
