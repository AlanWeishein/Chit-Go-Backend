package com.chitgo.usuarios.infraestructura.web;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.auditoria.aplicacion.ConsultarAuditoriaService;
import com.chitgo.auditoria.dominio.puertos.ConsultaDeAuditoria.Fila;
import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.usuarios.aplicacion.LocalDelGerenteService;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-16, pantalla P-19. La auditoría de HU-15 para el Gerente/Dueño, solo lectura. Mismos filtros, paginado y
 * respuesta que {@code /admin/auditoria}. El local sale del propietario; un {@code localId} que mande el pedido
 * tiene que ser el suyo. Sin lógica de negocio ni consultas propias.
 */
@RestController
@RequestMapping("/api/v1/gerente/auditoria")
@PreAuthorize("hasAuthority('auditoria.ver')")
public class AuditoriaGerenteController {

    private final LocalDelGerenteService local;
    private final ConsultarAuditoriaService auditoria;

    public AuditoriaGerenteController(LocalDelGerenteService local, ConsultarAuditoriaService auditoria) {
        this.local = local;
        this.auditoria = auditoria;
    }

    @GetMapping
    public PaginaDto<Fila> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long localId, @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String accion, @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return auditoria.consultar(usuario, local.resolver(usuario, localId), entidad, accion, usuarioId, desde,
                hasta, page, size);
    }
}
