package com.chitgo.auditoria.infraestructura.web;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.auditoria.aplicacion.ConsultarAuditoriaService;
import com.chitgo.auditoria.dominio.puertos.ConsultaDeAuditoria.Fila;
import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping("/api/v1/admin/auditoria")
@PreAuthorize("hasAuthority('auditoria.ver')")
public class AuditoriaController {
    private final ConsultarAuditoriaService auditoria;
    public AuditoriaController(ConsultarAuditoriaService auditoria) { this.auditoria = auditoria; }

    @GetMapping
    public PaginaDto<Fila> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required=false) Long localId, @RequestParam(required=false) String entidad,
            @RequestParam(required=false) String accion, @RequestParam(required=false) Long usuarioId,
            @RequestParam(required=false) LocalDate desde, @RequestParam(required=false) LocalDate hasta,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return auditoria.consultar(usuario, localId, entidad, accion, usuarioId, desde, hasta, page, size);
    }
}
