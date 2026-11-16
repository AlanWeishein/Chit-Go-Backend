package com.chitgo.usuarios.infraestructura.web;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.reportes.aplicacion.ConsultarReportesService;
import com.chitgo.reportes.aplicacion.ConsultarReportesService.TiemposDto;
import com.chitgo.reportes.aplicacion.ConsultarReportesService.VentasDto;
import com.chitgo.reportes.dominio.puertos.ReportesRepository.PlatoPedido;
import com.chitgo.usuarios.aplicacion.LocalDelGerenteService;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-16, pantalla P-19. Los reportes de HU-15 para el Gerente/Dueño, solo lectura. Mismos parámetros y misma
 * respuesta que {@code /admin/reportes}. El local sale del propietario; un {@code localId} que mande el pedido tiene
 * que ser el suyo. Sin lógica de negocio ni consultas propias.
 */
@RestController
@RequestMapping("/api/v1/gerente/reportes")
@PreAuthorize("hasAuthority('reporte.ver')")
public class ReportesGerenteController {

    private final LocalDelGerenteService local;
    private final ConsultarReportesService reportes;

    public ReportesGerenteController(LocalDelGerenteService local, ConsultarReportesService reportes) {
        this.local = local;
        this.reportes = reportes;
    }

    @GetMapping("/ventas")
    public VentasDto ventas(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long localId, @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportes.ventas(usuario, local.resolver(usuario, localId), desde, hasta, page, size);
    }

    @GetMapping("/tiempos-preparacion")
    public TiemposDto tiempos(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long localId, @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportes.tiempos(usuario, local.resolver(usuario, localId), desde, hasta, page, size);
    }

    @GetMapping("/platos-mas-pedidos")
    public PaginaDto<PlatoPedido> platos(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long localId, @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportes.platos(usuario, local.resolver(usuario, localId), desde, hasta, page, size);
    }
}
