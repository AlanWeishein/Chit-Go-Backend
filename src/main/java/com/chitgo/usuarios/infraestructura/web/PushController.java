package com.chitgo.usuarios.infraestructura.web;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.usuarios.aplicacion.SuscribirPushService;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/mozo/push")
@PreAuthorize("hasAuthority('aviso.atender')")
public class PushController {
    public record Claves(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 32) String auth) {}
    public record SuscripcionRequest(@NotBlank @Size(max = 2048) String endpoint, @NotNull @Valid Claves keys) {}
    public record BajaRequest(@NotBlank @Size(max = 2048) String endpoint) {}

    private final SuscribirPushService servicio;

    public PushController(SuscribirPushService servicio) { this.servicio = servicio; }

    @GetMapping("/clave-publica")
    public Map<String, Object> configuracion() { return servicio.configuracion(); }

    @PostMapping("/suscripcion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody SuscripcionRequest cuerpo) {
        servicio.registrar(usuario, cuerpo.endpoint(), cuerpo.keys().p256dh(), cuerpo.keys().auth());
    }

    @DeleteMapping("/suscripcion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody BajaRequest cuerpo) {
        servicio.eliminar(usuario, cuerpo.endpoint());
    }
}
