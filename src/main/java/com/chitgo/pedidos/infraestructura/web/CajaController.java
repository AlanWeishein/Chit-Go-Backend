package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.ColaDeAprobacionService;
import com.chitgo.pedidos.aplicacion.ResolverPedidoService;
import com.chitgo.pedidos.aplicacion.dto.PedidoPorAprobarDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-07. Sesión de Caja: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/caja/pedidos")
public class CajaController {

    private final ColaDeAprobacionService colaDeAprobacion;
    private final ResolverPedidoService resolverPedido;

    public CajaController(ColaDeAprobacionService colaDeAprobacion, ResolverPedidoService resolverPedido) {
        this.colaDeAprobacion = colaDeAprobacion;
        this.resolverPedido = resolverPedido;
    }

    /** Sin estado es PENDIENTE_APROBACION; cualquier otro da 400 (lo valida el Service). */
    @GetMapping
    @PreAuthorize("hasAuthority('pedido.aprobar_rechazar')")
    public List<PedidoPorAprobarDto> cola(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "estado", defaultValue = "PENDIENTE_APROBACION") String estado) {
        return colaDeAprobacion.obtener(usuario, estado);
    }

    @PostMapping("/{id}/aprobar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.aprobar_rechazar')")
    public void aprobar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        resolverPedido.aprobar(usuario, id);
    }

    /** Sin motivo, o con uno que no está en MotivoRechazo, 400. Si el front manda version, se ignora. */
    @PostMapping("/{id}/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.aprobar_rechazar')")
    public void rechazar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @Valid @RequestBody RechazarPedidoRequest cuerpo) {
        resolverPedido.rechazar(usuario, id, cuerpo.motivo());
    }
}
