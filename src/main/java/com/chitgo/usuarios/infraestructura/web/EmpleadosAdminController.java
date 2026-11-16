package com.chitgo.usuarios.infraestructura.web;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.usuarios.aplicacion.GestionarEmpleadosService;
import com.chitgo.usuarios.aplicacion.dto.AusenciaDto;
import com.chitgo.usuarios.aplicacion.dto.DatosDeTurno;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoResumenDto;
import com.chitgo.usuarios.aplicacion.dto.PaginaDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-14, pantallas P-16 y P-23. Sesión de Admin: sin sesión responde 401 y sin el permiso, 403. Sin lógica de
 * negocio.
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

    /** Blanquea el PIN: el nuevo es provisorio y levanta el bloqueo (CP-10-06). Responde el detalle. */
    @PostMapping("/{id}/pin")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto blanquearPin(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @Valid @RequestBody BlanqueoDePinRequest cuerpo) {
        return empleados.blanquearPin(usuario, id, cuerpo.pin());
    }

    /** Reemplaza la plantilla semanal entera. La lista vacía lo deja sin turnos. */
    @PutMapping("/{id}/turnos")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto reemplazarTurnos(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @RequestBody List<TurnoRequest> cuerpo) {
        List<DatosDeTurno> turnos = cuerpo == null ? null
                : cuerpo.stream().map(t -> t == null ? null : t.aDatos()).toList();
        return empleados.reemplazarTurnos(usuario, id, turnos);
    }

    /** Reemplaza las mesas del mozo. La lista vacía lo deja sin mesas. */
    @PutMapping("/{id}/mesas")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public EmpleadoDetalleDto reemplazarMesas(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @RequestBody MesasRequest cuerpo) {
        return empleados.reemplazarMesas(usuario, id, cuerpo.mesaIds());
    }

    @GetMapping("/{id}/ausencias")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public List<AusenciaDto> ausencias(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id) {
        return empleados.ausencias(usuario, id);
    }

    @PostMapping("/{id}/ausencias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public AusenciaDto cargarAusencia(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @RequestBody AusenciaRequest cuerpo) {
        return empleados.cargarAusencia(usuario, id, cuerpo.aDatos());
    }

    /** Termina la ausencia o cambia su fecha de vuelta. No se borra. */
    @PatchMapping("/{id}/ausencias/{ausenciaId}")
    @PreAuthorize("hasAuthority('empleado.gestionar')")
    public AusenciaDto cambiarFinDeAusencia(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable("id") Long id, @PathVariable("ausenciaId") Long ausenciaId,
            @RequestBody FinDeAusenciaRequest cuerpo) {
        return empleados.cambiarFinDeAusencia(usuario, id, ausenciaId, cuerpo.hasta());
    }
}
