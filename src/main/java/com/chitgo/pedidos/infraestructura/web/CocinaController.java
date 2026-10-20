package com.chitgo.pedidos.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.ColaDeCocinaService;
import com.chitgo.pedidos.aplicacion.dto.VistaDeCocinaDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-11. Sesión de Cocina: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/cocina/pedidos")
public class CocinaController {

    private final ColaDeCocinaService colaDeCocina;

    public CocinaController(ColaDeCocinaService colaDeCocina) {
        this.colaDeCocina = colaDeCocina;
    }

    @GetMapping
    public VistaDeCocinaDto cola(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return colaDeCocina.obtener(usuario);
    }

    @PostMapping("/{id}/en-preparacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enPreparacion(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        colaDeCocina.iniciarPreparacion(usuario, id);
    }

    @PostMapping("/{id}/listo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void listo(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        colaDeCocina.marcarListo(usuario, id);
    }
}
