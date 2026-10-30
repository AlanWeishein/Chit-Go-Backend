package com.chitgo.pedidos.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.pedidos.aplicacion.dto.CuentaPorCobrarDto;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-10: las cuentas por cobrar de Caja (RF-17), lo que muestra P-09. Incluye las abiertas, porque Caja puede
 * cerrar una que nadie pidió.
 *
 * <p>Son dos consultas, cuantas sean las cuentas: las cuentas con su mesa y después los pedidos de todas por lote.
 * El total y los sin servir salen de Pedido, con la misma regla que usa el cierre.
 */
@Service
public class CuentasPorCobrarService {

    private static final Set<EstadoCuenta> POR_COBRAR = EnumSet.of(EstadoCuenta.ABIERTA, EstadoCuenta.SOLICITADA);

    /** Primero las que alguien pidió, por cuándo la pidió; después las abiertas, por cuándo se abrieron. */
    private static final Comparator<Cuenta> ORDEN = Comparator
            .comparing((Cuenta c) -> c.getEstado() != EstadoCuenta.SOLICITADA)
            .thenComparing(CuentasPorCobrarService::desde, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Cuenta::getId);

    private final CuentaRepository cuentas;
    private final PedidoRepository pedidos;

    public CuentasPorCobrarService(CuentaRepository cuentas, PedidoRepository pedidos) {
        this.cuentas = cuentas;
        this.pedidos = pedidos;
    }

    /** @param estado ABIERTA, SOLICITADA o los dos separados por coma; cualquier otro valor da 400 */
    @Transactional(readOnly = true)
    public List<CuentaPorCobrarDto> obtener(UsuarioAutenticado usuario, String estado) {
        Set<EstadoCuenta> estados = estadosDe(estado);
        Long localId = localDe(usuario);
        List<Cuenta> delLocal = cuentas.listarDelLocal(localId, estados);
        if (delLocal.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Pedido>> porCuenta = pedidos.listarDeLasCuentas(delLocal.stream().map(Cuenta::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(p -> p.getCuenta().getId()));
        return delLocal.stream()
                .sorted(ORDEN)
                .map(c -> aDto(c, porCuenta.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    /** Una cuenta cerrada ya se cobró: CERRADA da 400 como cualquier otro valor. */
    private static Set<EstadoCuenta> estadosDe(String estado) {
        if (estado == null) {
            throw estadoInvalido();
        }
        Set<EstadoCuenta> estados = EnumSet.noneOf(EstadoCuenta.class);
        for (String parte : estado.split(",", -1)) {
            estados.add(POR_COBRAR.stream()
                    .filter(e -> e.name().equals(parte.trim()))
                    .findFirst()
                    .orElseThrow(CuentasPorCobrarService::estadoInvalido));
        }
        return estados;
    }

    private static SolicitudInvalidaException estadoInvalido() {
        return new SolicitudInvalidaException(
                "Las cuentas por cobrar se piden con estado ABIERTA, SOLICITADA o los dos separados por coma");
    }

    private static Instant desde(Cuenta cuenta) {
        return cuenta.getEstado() == EstadoCuenta.SOLICITADA ? cuenta.getSolicitadaEn() : cuenta.getAbiertaEn();
    }

    private static CuentaPorCobrarDto aDto(Cuenta cuenta, List<Pedido> deLaCuenta) {
        BigDecimal total = deLaCuenta.stream()
                .filter(Pedido::cuentaEnElTotal)
                .map(Pedido::total)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        int sinServir = (int) deLaCuenta.stream().filter(Pedido::estaSinServir).count();
        return new CuentaPorCobrarDto(cuenta.getId(), cuenta.getMesa().getNumero(), cuenta.getEstado().name(),
                cuenta.getAbiertaEn(), cuenta.getSolicitadaEn(), total, sinServir);
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
