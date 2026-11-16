package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.MesasSinMozoService;
import com.chitgo.pedidos.aplicacion.dto.MesaSinMozoDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-14: el estado de /alertas al reconectar (RNF-08). Lo pueden pedir los mismos que escuchan el topic, Caja y el
 * Admin (PermisosDeTopic). Sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio.
 */
@RestController
@RequestMapping("/api/v1/alertas")
public class AlertasController {

    private final MesasSinMozoService mesasSinMozo;

    public AlertasController(MesasSinMozoService mesasSinMozo) {
        this.mesasSinMozo = mesasSinMozo;
    }

    @GetMapping("/mesas-sin-mozo")
    @PreAuthorize("hasAnyAuthority('pedido.aprobar_rechazar', 'empleado.gestionar')")
    public List<MesaSinMozoDto> mesasSinMozo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return mesasSinMozo.listar(usuario);
    }
}
