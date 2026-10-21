package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.ColaDeAprobacionService;
import com.chitgo.pedidos.aplicacion.dto.PedidoPorAprobarDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-07. Sesión de Caja: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/caja/pedidos")
public class CajaController {

    private final ColaDeAprobacionService colaDeAprobacion;

    public CajaController(ColaDeAprobacionService colaDeAprobacion) {
        this.colaDeAprobacion = colaDeAprobacion;
    }

    /** Sin estado es PENDIENTE_APROBACION; cualquier otro da 400 (lo valida el Service). */
    @GetMapping
    @PreAuthorize("hasAuthority('pedido.aprobar_rechazar')")
    public List<PedidoPorAprobarDto> cola(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "estado", defaultValue = "PENDIENTE_APROBACION") String estado) {
        return colaDeAprobacion.obtener(usuario, estado);
    }
}
