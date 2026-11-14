package com.chitgo.usuarios.infraestructura.web;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.GestionarEmpleadosService;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoResumenDto;
import com.chitgo.usuarios.aplicacion.dto.PaginaDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-14, pantallas P-16 y P-23. Sesión de Admin: sin sesión responde 401 y sin el permiso, 403. Sin lógica de
 * negocio. Los turnos, las mesas, las ausencias y el blanqueo del PIN cuelgan de acá y llegan en las tareas
 * siguientes de la ST-22.
 */
@RestController
@RequestMapping("/api/v1/admin/empleados")
public class EmpleadosAdminController {

    private final GestionarEmpleadosService empleados;

    public EmpleadosAdminController(GestionarEmpleadosService empleados) {
        this.empleados = empleados;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public PaginaDto<EmpleadoResumenDto> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return empleados.listar(usuario, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto detalle(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id) {
        return empleados.detalle(usuario, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto crear(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody AltaDeEmpleadoRequest cuerpo) {
        return empleados.crear(usuario, cuerpo.aDatos());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto editar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @Valid @RequestBody EdicionDeEmpleadoRequest cuerpo) {
        return empleados.editar(usuario, id, cuerpo.aDatos());
    }

    /** Baja lógica. Darlo de baja dos veces responde lo mismo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public void darDeBaja(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        empleados.darDeBaja(usuario, id);
    }
}
