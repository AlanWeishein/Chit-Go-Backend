package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.menu.dominio.Carta;
import com.chitgo.menu.dominio.CategoriaDeCarta;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.PlatoDeCarta;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.EstadoDeMesaDto;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeSesion;
import com.chitgo.pedidos.aplicacion.dto.SesionDeMesaDto;
import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.TipoAviso;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.AvisoRepository;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;

/** HU-01: escanear el QR, abrir la cuenta si no hay una y ver la carta. */
@Service
public class EntrarALaMesaService {

    /** El mismo para cualquier token que no sirve: no dice nada de ningún local ni de otras mesas. */
    static final String MESA_NO_ENCONTRADA = "No encontramos esta mesa. Pedile ayuda al mozo.";
    static final String MESA_NO_DISPONIBLE = "Esta mesa no está disponible.";

    private final MesaRepository mesas;
    private final CuentaRepository cuentas;
    private final ClienteSesionRepository sesiones;
    private final AvisoRepository avisos;
    private final MozoDeTurno mozoDeTurno;
    private final CartaRepository carta;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    public EntrarALaMesaService(MesaRepository mesas, CuentaRepository cuentas, ClienteSesionRepository sesiones,
            AvisoRepository avisos, MozoDeTurno mozoDeTurno, CartaRepository carta, PublicadorDeEventos eventos,
            Reloj reloj) {
        this.mesas = mesas;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.avisos = avisos;
        this.mozoDeTurno = mozoDeTurno;
        this.carta = carta;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /** GET /public/mesas/{tokenQR}: datos de la mesa, sin abrir nada. Una mesa inhabilitada responde 200 con disponible = false. */
    @Transactional(readOnly = true)
    public EstadoDeMesaDto consultar(String tokenQr) {
        Mesa mesa = mesas.buscarPorTokenQr(tokenQr)
                .orElseThrow(() -> new NoEncontradoException(MESA_NO_ENCONTRADA));
        boolean hayCuenta = cuentas.buscarNoCerradaDeMesa(mesa.getId()).isPresent();
        return new EstadoDeMesaDto(mesa.getNumero(), !mesa.isInhabilitada(), hayCuenta);
    }

    /**
     * POST /public/mesas/{tokenQR}/sesiones. El primero que escanea una mesa sin cuenta la abre y queda como
     * iniciador (D-03): es el único que puede pedir. Los demás entran en modo lectura. Si el celular manda su
     * token y sigue siendo de la cuenta abierta, se le devuelve la misma sesión: el iniciador que recarga
     * sigue siendo el iniciador.
     */
    @Transactional
    public ResultadoDeSesion abrirSesion(String tokenQr, String tokenSesionExistente) {
        // La fila de la mesa queda bloqueada: dos escaneos a la vez se hacen de a uno y se abre una sola cuenta.
        Mesa mesa = mesas.buscarPorTokenQrParaAbrirCuenta(tokenQr)
                .orElseThrow(() -> new NoEncontradoException(MESA_NO_ENCONTRADA));
        Optional<Cuenta> abierta = cuentas.buscarNoCerradaDeMesa(mesa.getId());

        Optional<ResultadoDeSesion> repetida = sesionVigente(tokenSesionExistente, abierta);
        if (repetida.isPresent()) {
            return repetida.get();
        }

        Instant ahora = reloj.ahora();
        if (abierta.isEmpty()) {
            if (mesa.isInhabilitada()) {
                throw new ReglaDeNegocioException(MESA_NO_DISPONIBLE);
            }
            return guardarSesion(abrirCuenta(mesa, ahora), true);
        }
        return guardarSesion(abierta.get(), false);
    }

    /** GET /public/mesas/{tokenQR}/menu?etiqueta=...: la carta del local de esa mesa, filtrada. */
    @Transactional(readOnly = true)
    public Carta obtenerCarta(String tokenQr, Set<String> etiquetas) {
        Mesa mesa = mesas.buscarPorTokenQr(tokenQr)
                .orElseThrow(() -> new NoEncontradoException(MESA_NO_ENCONTRADA));
        return armarCarta(carta.listarActivosDelLocal(mesa.getLocalId())).filtrarPorEtiquetas(etiquetas);
    }

    static Carta armarCarta(List<Plato> platos) {
        Map<Long, List<PlatoDeCarta>> porCategoria = new LinkedHashMap<>();
        Map<Long, CategoriaMenu> categorias = new LinkedHashMap<>();
        for (Plato p : platos) {
            categorias.putIfAbsent(p.getCategoria().getId(), p.getCategoria());
            porCategoria.computeIfAbsent(p.getCategoria().getId(), id -> new ArrayList<>())
                    .add(new PlatoDeCarta(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                            p.getImagenUrl(), p.isDestacado(),
                            p.getEtiquetas().stream().map(Etiqueta::getNombre).sorted().toList()));
        }
        List<CategoriaDeCarta> resultado = categorias.values().stream()
                .sorted(Comparator.comparing(CategoriaMenu::getOrden)
                        .thenComparing(CategoriaMenu::getId))
                .map(c -> new CategoriaDeCarta(c.getId(), c.getNombre(), c.getOrden(),
                        porCategoria.get(c.getId()).stream()
                                .sorted(Comparator.comparing(PlatoDeCarta::nombre).thenComparing(PlatoDeCarta::id))
                                .toList()))
                .toList();
        return Carta.de(resultado);
    }

    private Optional<ResultadoDeSesion> sesionVigente(String tokenSesion, Optional<Cuenta> abierta) {
        if (tokenSesion == null || abierta.isEmpty()) {
            return Optional.empty();
        }
        return sesiones.buscarPorToken(tokenSesion)
                .filter(s -> s.getCuenta().getId().equals(abierta.get().getId()))
                .map(s -> new ResultadoDeSesion(new SesionDeMesaDto(s.getTokenSesion(), s.isEsIniciador()), false));
    }

    private Cuenta abrirCuenta(Mesa mesa, Instant ahora) {
        Optional<Long> mozo = mozoDeTurno.buscarParaMesa(mesa.getId(), ahora);
        Cuenta cuenta = cuentas.guardar(new Cuenta(mesa.getLocalId(), mesa, mozo.orElse(null), ahora));
        if (mozo.isEmpty()) {
            // La cuenta se abre igual y se avisa a Caja o al Admin (decidido el 06/10).
            avisos.guardar(new Aviso(mesa.getLocalId(), mesa, TipoAviso.MESA_SIN_MOZO));
            eventos.aAlertas(mesa.getLocalId(), EventoTiempoReal.de(TipoEvento.MESA_SIN_MOZO, null, mesa.getId(),
                    null, "La mesa " + mesa.getNumero() + " no tiene mozo de turno", ahora));
        }
        return cuenta;
    }

    private ResultadoDeSesion guardarSesion(Cuenta cuenta, boolean iniciadora) {
        ClienteSesion sesion = sesiones.guardar(
                new ClienteSesion(cuenta.getLocalId(), cuenta, UUID.randomUUID().toString(), iniciadora));
        return new ResultadoDeSesion(new SesionDeMesaDto(sesion.getTokenSesion(), sesion.isEsIniciador()), true);
    }
}
