package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.aplicacion.dto.AvisoDto;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;

/** HU-03. El llamado lo puede hacer cualquier comensal; solo el iniciador pide la cuenta. */
@Service
public class AvisosClienteService {
    private final ClienteSesionRepository sesiones;
    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;
    private final AvisoRepository avisos;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    public AvisosClienteService(ClienteSesionRepository sesiones, CuentaRepository cuentas, PedidoRepository pedidos,
            AvisoRepository avisos, PublicadorDeEventos eventos, Reloj reloj) {
        this.sesiones = sesiones;
        this.cuentas = cuentas;
        this.pedidos = pedidos;
        this.avisos = avisos;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public AvisoDto llamarMozo(String tokenSesion) {
        Cuenta cuenta = sesion(tokenSesion).getCuenta();
        cuenta.exigirNoCerrada();
        if (avisos.hayPendiente(cuenta.getId(), TipoAviso.LLAMAR_MOZO)) {
            throw new ReglaDeNegocioException("Ya avisamos al mozo");
        }
        // Dos llamados simultáneos leen la misma versión: solo uno confirma, sin duplicar el aviso.
        // También compite con el cierre de cuenta antes del INSERT del aviso.
        cuentas.subirVersion(cuenta);
        return crear(cuenta, TipoAviso.LLAMAR_MOZO);
    }

    @Transactional
    public void solicitarCuenta(String tokenSesion) {
        ClienteSesion sesion = sesion(tokenSesion);
        if (!sesion.isEsIniciador()) {
            throw new AccesoDenegadoException("Solo quien abrió la cuenta puede solicitarla");
        }
        Cuenta cuenta = sesion.getCuenta();
        cuenta.exigirNoCerrada();
        if (pedidos.listarDeLaCuenta(cuenta.getId()).stream().anyMatch(Pedido::estaSinServir)) {
            throw new ReglaDeNegocioException("Hay pedidos sin servir. Esperá a que el mozo los sirva para pedir la cuenta.");
        }
        cuenta.solicitar(reloj.ahora());
        cuentas.guardar(cuenta); // Compite con un envío nuevo o con el cierre de Caja.
        // Si un pedido reabrió la cuenta, el aviso anterior no se duplica mientras siga pendiente.
        if (!avisos.hayPendiente(cuenta.getId(), TipoAviso.CUENTA_SOLICITADA)) {
            crear(cuenta, TipoAviso.CUENTA_SOLICITADA);
        }
        eventos.aCaja(cuenta.getLocalId(), EventoTiempoReal.deCuenta(TipoEvento.CUENTA_SOLICITADA,
                cuenta.getId(), cuenta.getMesa().getId(), cuenta.getEstado().name(), reloj.ahora()));
    }

    private ClienteSesion sesion(String token) {
        return sesiones.buscarPorToken(token == null ? "" : token)
                .orElseThrow(() -> new AccesoDenegadoException("La sesión no es válida"));
    }

    private AvisoDto crear(Cuenta cuenta, TipoAviso tipo) {
        Instant ahora = reloj.ahora();
        Aviso aviso = avisos.guardar(new Aviso(cuenta, tipo, ahora));
        eventos.aMozos(cuenta.getLocalId(), new EventoTiempoReal(TipoEvento.AVISO_CREADO, null,
                cuenta.getMesa().getId(), cuenta.getId(), null, cuenta.getEstado().name(), tipo.name(),
                ahora.toString()));
        return new AvisoDto(aviso.getId(), cuenta.getMesa().getId(), cuenta.getMesa().getNumero(),
                tipo.name(), aviso.getCreadoEn(), null, null);
    }
}
