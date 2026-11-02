package com.chitgo.pedidos.infraestructura.web;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.pedidos.aplicacion.GestionDeColaService;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/caja/pedidos")
public class GestionDeColaController {
    public record PrioridadRequest(@NotNull Boolean prioridadManual) {}
    public record CancelarLoteRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> pedidoIds) {}

    private final GestionDeColaService servicio;

    public GestionDeColaController(GestionDeColaService servicio) {
        this.servicio = servicio;
    }

    @PutMapping("/{id}/prioridad")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.priorizar')")
    public void priorizar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @Valid @RequestBody PrioridadRequest cuerpo) {
        servicio.priorizar(usuario, id, cuerpo.prioridadManual());
    }

    @PostMapping("/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.cancelar')")
    public void cancelar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        servicio.cancelar(usuario, List.of(id));
    }

    @PostMapping("/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pedido.cancelar')")
    public void cancelarLote(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CancelarLoteRequest cuerpo) {
        servicio.cancelar(usuario, cuerpo.pedidoIds());
    }
}
