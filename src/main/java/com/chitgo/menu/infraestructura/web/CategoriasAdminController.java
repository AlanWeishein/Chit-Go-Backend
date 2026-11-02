package com.chitgo.menu.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.menu.aplicacion.GestionarCategoriasService;
import com.chitgo.menu.aplicacion.dto.CategoriaAdminDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-12, pantalla P-14. Sesión de Admin: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/admin/categorias")
public class CategoriasAdminController {

    private final GestionarCategoriasService categorias;

    public CategoriasAdminController(GestionarCategoriasService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public List<CategoriaAdminDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return categorias.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public CategoriaAdminDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CategoriaRequest cuerpo) {
        return categorias.crear(usuario, cuerpo.nombre());
    }

    /** Spring prefiere la ruta literal a /{id}: "orden" no se toma como un id. */
    @PutMapping("/orden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public void ordenar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody OrdenDeCategoriasRequest cuerpo) {
        categorias.ordenar(usuario, cuerpo.ids());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public CategoriaAdminDto renombrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @Valid @RequestBody CategoriaRequest cuerpo) {
        return categorias.renombrar(usuario, id, cuerpo.nombre());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public void borrar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        categorias.borrar(usuario, id);
    }
}
