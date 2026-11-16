package com.chitgo.menu.aplicacion;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.menu.aplicacion.dto.CambioDeDisponibilidadDto;
import com.chitgo.menu.aplicacion.dto.PlatoDisponibilidadDto;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.AvisoDeDisponibilidad;
import com.chitgo.menu.dominio.puertos.PedidosConPlato;
import com.chitgo.menu.dominio.puertos.PedidosConPlato.PedidoAfectado;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-09: Caja, o el Admin como respaldo, apaga un plato o lo vuelve a prender (RF-20) y ve los pedidos que lo
 * tienen (RF-21). Es también sacar un plato de la carta (HU-12, decidido el 29/10). La cancelación de esos
 * pedidos no está acá.
 *
 * <p>Un plato de otro local da 404, el mismo que si no existiera (RNF-10).
 */
@Service
public class CambiarDisponibilidadService {

    static final String ENTIDAD = "PLATO";
    static final String ACCION = "CAMBIAR_DISPONIBILIDAD";

    private final PlatoRepository platos;
    private final PedidosConPlato pedidosConPlato;
    private final AvisoDeDisponibilidad aviso;
    private final Auditor auditor;

    public CambiarDisponibilidadService(PlatoRepository platos, PedidosConPlato pedidosConPlato,
                                        AvisoDeDisponibilidad aviso, Auditor auditor) {
        this.platos = platos;
        this.pedidosConPlato = pedidosConPlato;
        this.aviso = aviso;
        this.auditor = auditor;
    }

    /** P-10: todos los del local, también los apagados, en el orden de la carta. */
    @Transactional(readOnly = true)
    public List<PlatoDisponibilidadDto> listar(UsuarioAutenticado usuario) {
        return platos.listarDelLocal(localDe(usuario)).stream().map(CambiarDisponibilidadService::aDto).toList();
    }

    /**
     * Si el plato ya estaba así, no guarda, no audita ni avisa, y responde igual con los afectados de ahora: un doble
     * toque no deja dos registros ni dos avisos por celular (29/10). Al apagarlo, devuelve los pedidos que Caja puede
     * cancelar; al prenderlo, ninguno.
     */
    @Transactional
    public CambioDeDisponibilidadDto cambiar(UsuarioAutenticado usuario, Long platoId, boolean disponible) {
        Long localId = localDe(usuario);
        Plato plato = platos.buscarPorId(platoId, localId)
                .orElseThrow(() -> new NoEncontradoException("El plato no existe"));

        if (plato.cambiarDisponibilidad(disponible)) {
            platos.guardar(plato);
            auditor.registrar(localId, usuario.usuarioId(), ACCION, ENTIDAD, plato.getId(),
                    Map.of("disponible", !disponible), Map.of("disponible", disponible));
            aviso.platoCambio(localId, plato.getId(), disponible);
        }
        List<PedidoAfectado> afectados = disponible ? List.of() : pedidosConPlato.afectadosPor(plato.getId(), localId);
        return new CambioDeDisponibilidadDto(plato.getId(), plato.isActivo(), afectados);
    }

    private static PlatoDisponibilidadDto aDto(Plato plato) {
        CategoriaMenu categoria = plato.getCategoria();
        return new PlatoDisponibilidadDto(plato.getId(), plato.getNombre(),
                new PlatoDisponibilidadDto.CategoriaDto(categoria.getId(), categoria.getNombre()), plato.isActivo());
    }

    /** El Gerente/Dueño no tiene local fijo y no opera la caja. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
