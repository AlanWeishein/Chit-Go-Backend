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

import com.chitgo.menu.aplicacion.GestionarEtiquetasService;
import com.chitgo.menu.aplicacion.dto.EtiquetaAdminDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-12, pantalla P-14. Sesión de Admin: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/admin/etiquetas")
public class EtiquetasAdminController {

    private final GestionarEtiquetasService etiquetas;

    public EtiquetasAdminController(GestionarEtiquetasService etiquetas) {
        this.etiquetas = etiquetas;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public List<EtiquetaAdminDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return etiquetas.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public EtiquetaAdminDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody EtiquetaRequest cuerpo) {
        return etiquetas.crear(usuario, cuerpo.nombre());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public EtiquetaAdminDto renombrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @Valid @RequestBody EtiquetaRequest cuerpo) {
        return etiquetas.renombrar(usuario, id, cuerpo.nombre());
    }

    /** La etiqueta se va también de los platos que la tenían. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public void borrar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        etiquetas.borrar(usuario, id);
    }
}
