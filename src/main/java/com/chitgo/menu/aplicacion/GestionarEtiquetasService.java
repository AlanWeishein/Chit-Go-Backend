package com.chitgo.menu.aplicacion;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.menu.aplicacion.dto.EtiquetaAdminDto;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.EtiquetaRepetidaException;
import com.chitgo.menu.dominio.Nombres;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-12: el Admin crea, renombra y borra las etiquetas de alérgenos del local (RF-24).
 *
 * <p>Una etiqueta no se repite dentro del local (CP-25-05): se busca antes, y si dos Admin la crean a la vez lo
 * frena el UNIQUE de la base. Las dos dan 409 "Esa etiqueta ya existe" (decidido el 29/10). Una etiqueta de otro
 * local da 404, el mismo que si no existiera (RNF-10).
 */
@Service
public class GestionarEtiquetasService {

    static final String ENTIDAD = "ETIQUETA";

    private final EtiquetaRepository etiquetas;
    private final Auditor auditor;

    public GestionarEtiquetasService(EtiquetaRepository etiquetas, Auditor auditor) {
        this.etiquetas = etiquetas;
        this.auditor = auditor;
    }

    /** P-14: las del local por nombre. */
    @Transactional(readOnly = true)
    public List<EtiquetaAdminDto> listar(UsuarioAutenticado usuario) {
        return etiquetas.listarDelLocal(localDe(usuario)).stream().map(GestionarEtiquetasService::aDto).toList();
    }

    @Transactional
    public EtiquetaAdminDto crear(UsuarioAutenticado usuario, String nombre) {
        Long localId = localDe(usuario);
        Etiqueta etiqueta = new Etiqueta(localId, nombre);
        if (etiquetas.existeConNombre(localId, etiqueta.getNombre(), null)) {
            throw new EtiquetaRepetidaException();
        }
        etiqueta = etiquetas.guardar(etiqueta);

        auditor.registrar(localId, usuario.usuarioId(), "CREAR_ETIQUETA", ENTIDAD, etiqueta.getId(), null,
                Map.of("nombre", etiqueta.getNombre()));
        return aDto(etiqueta);
    }

    /**
     * Renombrarla a una variante de su propio nombre ("Sin TACC" a "Sin Tacc") vale. Si el nombre es el mismo, no
     * guarda ni audita.
     */
    @Transactional
    public EtiquetaAdminDto renombrar(UsuarioAutenticado usuario, Long etiquetaId, String nombre) {
        Long localId = localDe(usuario);
        Etiqueta etiqueta = buscarEtiqueta(etiquetaId, localId);
        // Se busca antes de cambiar la entidad: si no, la consulta haría el flush del UPDATE y el UNIQUE
        // saltaría ahí, fuera de guardar.
        String nuevo = Nombres.obligatorio(nombre);
        String anterior = etiqueta.getNombre();
        if (anterior.equals(nuevo)) {
            return aDto(etiqueta);
        }
        if (etiquetas.existeConNombre(localId, nuevo, etiqueta.getId())) {
            throw new EtiquetaRepetidaException();
        }
        etiqueta.renombrar(nuevo);
        etiquetas.guardar(etiqueta);

        auditor.registrar(localId, usuario.usuarioId(), "EDITAR_ETIQUETA", ENTIDAD, etiqueta.getId(),
                Map.of("nombre", anterior), Map.of("nombre", nuevo));
        return aDto(etiqueta);
    }

    /** Se va también de los platos que la tenían (plato_etiqueta borra en cascada). */
    @Transactional
    public void borrar(UsuarioAutenticado usuario, Long etiquetaId) {
        Long localId = localDe(usuario);
        Etiqueta etiqueta = buscarEtiqueta(etiquetaId, localId);
        etiquetas.borrar(etiqueta);
        auditor.registrar(localId, usuario.usuarioId(), "BORRAR_ETIQUETA", ENTIDAD, etiqueta.getId(),
                Map.of("nombre", etiqueta.getNombre()), null);
    }

    private Etiqueta buscarEtiqueta(Long etiquetaId, Long localId) {
        return etiquetas.buscarPorId(etiquetaId, localId)
                .orElseThrow(() -> new NoEncontradoException("La etiqueta no existe"));
    }

    private static EtiquetaAdminDto aDto(Etiqueta etiqueta) {
        return new EtiquetaAdminDto(etiqueta.getId(), etiqueta.getNombre());
    }

    /** El Gerente/Dueño no tiene local fijo y no gestiona la carta. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
