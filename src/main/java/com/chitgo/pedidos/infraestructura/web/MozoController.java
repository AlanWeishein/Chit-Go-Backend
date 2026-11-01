package com.chitgo.pedidos.infraestructura.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.pedidos.aplicacion.MozoService;
import com.chitgo.pedidos.aplicacion.dto.AvisoDto;
import com.chitgo.pedidos.aplicacion.dto.MesaDeMozoDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/mozo")
public class MozoController {
    private final MozoService servicio;

    public MozoController(MozoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mesas")
    @PreAuthorize("hasAuthority('mesa.ver_asignadas')")
    public List<MesaDeMozoDto> mesas(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "todas", defaultValue = "false") boolean todas) {
        return servicio.mesas(usuario, todas);
    }

    @GetMapping("/avisos")
    @PreAuthorize("hasAuthority('aviso.atender')")
    public List<AvisoDto> avisos(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "pendientes", defaultValue = "true") boolean pendientes) {
        return servicio.avisos(usuario, pendientes);
    }

    @PostMapping("/avisos/{id}/atender")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('aviso.atender')")
    public void atender(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        servicio.atender(usuario, id);
    }

    @PostMapping("/pedidos/{id}/servido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.marcar_servido')")
    public void servir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        servicio.servir(usuario, id);
    }
}
