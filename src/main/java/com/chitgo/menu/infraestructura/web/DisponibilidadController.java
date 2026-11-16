package com.chitgo.menu.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.menu.aplicacion.CambiarDisponibilidadService;
import com.chitgo.menu.aplicacion.dto.CambioDeDisponibilidadDto;
import com.chitgo.menu.aplicacion.dto.PlatoDisponibilidadDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-09, pantallas P-10 (Caja) y P-14 (Admin, también para sacar un plato de la carta). Sin sesión responde 401 y
 * sin el permiso, 403: Cocina no lo tiene (CP-23-07). Sin lógica de negocio.
 */
@RestController
@RequestMapping("/api/v1/platos")
public class DisponibilidadController {

    private final CambiarDisponibilidadService disponibilidad;

    public DisponibilidadController(CambiarDisponibilidadService disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    /** Caja no tiene menu.gestionar y no puede usar GET /admin/platos (propuesto en el chat el 29/10). */
    @GetMapping
    @PreAuthorize("hasAuthority('plato.cambiar_disponibilidad')")
    public List<PlatoDisponibilidadDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return disponibilidad.listar(usuario);
    }

    @PatchMapping("/{id}/disponibilidad")
    @PreAuthorize("hasAuthority('plato.cambiar_disponibilidad')")
    public CambioDeDisponibilidadDto cambiar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @Valid @RequestBody DisponibilidadRequest cuerpo) {
        return disponibilidad.cambiar(usuario, id, cuerpo.disponible());
    }
}
