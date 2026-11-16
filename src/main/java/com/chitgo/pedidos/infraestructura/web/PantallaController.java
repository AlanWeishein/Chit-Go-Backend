package com.chitgo.pedidos.infraestructura.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.pedidos.aplicacion.PantallaService;
import com.chitgo.pedidos.aplicacion.PantallaService.*;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
public class PantallaController {
    private final PantallaService pantalla;
    public PantallaController(PantallaService pantalla) { this.pantalla = pantalla; }

    @GetMapping("/api/v1/pantalla/{tokenPantalla}/pedidos-en-curso")
    public PantallaDto consultar(@PathVariable String tokenPantalla) { return pantalla.consultar(tokenPantalla); }

    @GetMapping("/api/v1/admin/pantalla")
    @PreAuthorize("hasAuthority('local.configurar')")
    public AccesoPantalla acceso(@AuthenticationPrincipal UsuarioAutenticado usuario) { return pantalla.acceso(usuario); }
}
