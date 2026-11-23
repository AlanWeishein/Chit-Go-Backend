package com.chitgo.menu.infraestructura.web;

import java.io.IOException;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chitgo.menu.aplicacion.GestionarPlatosService;
import com.chitgo.menu.aplicacion.dto.PlatoAdminDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-12, pantalla P-14. Sesión de Admin: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio.
 * No hay DELETE: sacar un plato de la carta es PATCH /platos/{id}/disponibilidad (ST-12, decidido el 29/10).
 */
@RestController
@RequestMapping("/api/v1/admin/platos")
public class PlatosAdminController {

    private final GestionarPlatosService platos;

    public PlatosAdminController(GestionarPlatosService platos) {
        this.platos = platos;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public List<PlatoAdminDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return platos.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public PlatoAdminDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody PlatoRequest cuerpo) {
        return platos.crear(usuario, cuerpo.aDatos());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public PlatoAdminDto editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @Valid @RequestBody PlatoRequest cuerpo) {
        return platos.editar(usuario, id, cuerpo.aDatos());
    }

    @PatchMapping("/{id}/destacado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public void destacar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @Valid @RequestBody DestacarPlatoRequest cuerpo) {
        platos.destacar(usuario, id, cuerpo.destacado());
    }

    /**
     * ST-29: la foto del plato, en el campo {@code imagen} de un multipart (no en base64, decidido el 17/11). Si no
     * viene el campo, el servicio responde "Elegí una imagen"; si pesa más de 3 MB, Spring lo corta antes de llegar
     * acá y ManejadorDeErrores da el 400.
     */
    @PostMapping(path = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('menu.gestionar')")
    public PlatoAdminDto cambiarImagen(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @RequestPart(name = "imagen", required = false) MultipartFile imagen)
            throws IOException {
        return platos.cambiarImagen(usuario, id, imagen == null ? null : imagen.getBytes());
    }
}
