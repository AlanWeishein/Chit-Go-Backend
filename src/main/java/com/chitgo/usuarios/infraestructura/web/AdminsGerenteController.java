package com.chitgo.usuarios.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.GestionarAdminsService;
import com.chitgo.usuarios.aplicacion.dto.AdminDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-16, pantalla P-19. Sesión del Gerente/Dueño: sin sesión responde 401 y sin el permiso, 403 (el Admin también,
 * CP-31-05). No recibe localId: el local sale del propietario. Sin lógica de negocio.
 */
@RestController
@RequestMapping("/api/v1/gerente/admins")
public class AdminsGerenteController {

    private final GestionarAdminsService admins;

    public AdminsGerenteController(GestionarAdminsService admins) {
        this.admins = admins;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('admin.gestionar')")
    public List<AdminDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return admins.listar(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('admin.gestionar')")
    public AdminDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody AltaDeAdminRequest cuerpo) {
        return admins.crear(usuario, cuerpo.aDatos());
    }

    /** Baja o reactivación. Repetir el mismo estado responde lo mismo. */
    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasAuthority('admin.gestionar')")
    public AdminDto cambiarActivo(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @RequestBody ActivoRequest cuerpo) {
        return admins.cambiarActivo(usuario, id, cuerpo.activo());
    }

    @PostMapping("/{id}/desbloquear")
    @PreAuthorize("hasAuthority('admin.gestionar')")
    public AdminDto desbloquear(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        return admins.desbloquear(usuario, id);
    }
}
