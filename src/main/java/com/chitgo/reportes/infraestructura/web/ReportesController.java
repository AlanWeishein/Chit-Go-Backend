package com.chitgo.reportes.infraestructura.web;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.reportes.aplicacion.ConsultarReportesService;
import com.chitgo.reportes.aplicacion.ConsultarReportesService.*;
import com.chitgo.reportes.dominio.puertos.ReportesRepository.PlatoPedido;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@RestController
@RequestMapping({"/api/v1/admin/reportes", "/api/v1/gerente/reportes"})
@PreAuthorize("hasAuthority('reporte.ver')")
public class ReportesController {
    private final ConsultarReportesService reportes;
    public ReportesController(ConsultarReportesService reportes) { this.reportes = reportes; }

    @GetMapping("/ventas")
    public VentasDto ventas(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam(required=false) Long localId,
            @RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return reportes.ventas(usuario, localId, desde, hasta, page, size);
    }

    @GetMapping("/tiempos-preparacion")
    public TiemposDto tiempos(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam(required=false) Long localId,
            @RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return reportes.tiempos(usuario, localId, desde, hasta, page, size);
    }

    @GetMapping("/platos-mas-pedidos")
    public PaginaDto<PlatoPedido> platos(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam(required=false) Long localId,
            @RequestParam LocalDate desde, @RequestParam LocalDate hasta,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return reportes.platos(usuario, localId, desde, hasta, page, size);
    }
}
