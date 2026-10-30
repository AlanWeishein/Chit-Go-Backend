package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.pedidos.aplicacion.CerrarCuentaService;
import com.chitgo.pedidos.aplicacion.CuentasPorCobrarService;
import com.chitgo.pedidos.aplicacion.ReimprimirTicketService;
import com.chitgo.pedidos.aplicacion.dto.CuentaPorCobrarDto;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** HU-10. Sesión de Caja: sin sesión responde 401 y sin el permiso, 403. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/v1/caja/cuentas")
public class CuentasController {

    private final CuentasPorCobrarService cuentasPorCobrar;
    private final CerrarCuentaService cerrarCuenta;
    private final ReimprimirTicketService reimprimirTicket;

    public CuentasController(CuentasPorCobrarService cuentasPorCobrar, CerrarCuentaService cerrarCuenta,
            ReimprimirTicketService reimprimirTicket) {
        this.cuentasPorCobrar = cuentasPorCobrar;
        this.cerrarCuenta = cerrarCuenta;
        this.reimprimirTicket = reimprimirTicket;
    }

    /** Sin estado son ABIERTA y SOLICITADA; CERRADA o cualquier otro valor da 400 (lo valida el Service). */
    @GetMapping
    @PreAuthorize("hasAuthority('cuenta.cerrar')")
    public List<CuentaPorCobrarDto> porCobrar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(name = "estado", defaultValue = "ABIERTA,SOLICITADA") String estado) {
        return cuentasPorCobrar.obtener(usuario, estado);
    }

    /**
     * Cierra la cuenta y devuelve el ticket. El body es opcional: sin body es {@code {confirmar: false}}. Si hay que
     * confirmar, 409 con {@code codigo: CONFIRMAR_CIERRE} (lo arma ErroresDeCierre).
     */
    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasAuthority('cuenta.cerrar')")
    public TicketDto cerrar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id,
            @RequestBody(required = false) CerrarCuentaRequest cuerpo) {
        if (cuerpo == null) {
            return cerrarCuenta.cerrar(usuario, id, false, null);
        }
        return cerrarCuenta.cerrar(usuario, id, cuerpo.confirma(), cuerpo.pedidosACancelar());
    }

    /** El mismo ticket del cierre. Una cuenta que no está cerrada da 404, igual que una de otro local. */
    @GetMapping("/{id}/ticket")
    @PreAuthorize("hasAuthority('cuenta.cerrar')")
    public TicketDto ticket(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable("id") Long id) {
        return reimprimirTicket.reimprimir(usuario, id);
    }
}
