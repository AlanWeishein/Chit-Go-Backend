package com.chitgo.menu.aplicacion;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.aplicacion.dto.CategoriaAdminDto;
import com.chitgo.menu.dominio.CategoriaConPlatosException;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.PlatosDeCategoria;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * HU-12: el Admin crea, renombra, ordena y borra las categorías de la carta (RF-24).
 *
 * <p>Una categoría de otro local da 404, el mismo que si no existiera (RNF-10).
 */
@Service
public class GestionarCategoriasService {

    static final String ENTIDAD = "CATEGORIA_MENU";
    static final String ORDEN_INCOMPLETO = "El orden tiene que traer todas las categorías del local, una vez cada una";

    private final CategoriaMenuRepository categorias;
    private final PlatoRepository platos;
    private final Auditor auditor;

    public GestionarCategoriasService(CategoriaMenuRepository categorias, PlatoRepository platos, Auditor auditor) {
        this.categorias = categorias;
        this.platos = platos;
        this.auditor = auditor;
    }

    /** P-14: en su orden, con cuántos platos tiene cada una y cuántos están fuera de carta. */
    @Transactional(readOnly = true)
    public List<CategoriaAdminDto> listar(UsuarioAutenticado usuario) {
        Long localId = localDe(usuario);
        Map<Long, PlatosDeCategoria> conteo = platos.contarPorCategoria(localId);
        return categorias.listarDelLocal(localId).stream()
                .map(c -> aDto(c, conteo.getOrDefault(c.getId(), PlatosDeCategoria.NINGUNO)))
                .toList();
    }

    /** Va al final del orden. */
    @Transactional
    public CategoriaAdminDto crear(UsuarioAutenticado usuario, String nombre) {
        Long localId = localDe(usuario);
        int ultimo = categorias.listarDelLocal(localId).stream().mapToInt(CategoriaMenu::getOrden).max().orElse(0);
        CategoriaMenu categoria = categorias.guardar(new CategoriaMenu(localId, nombre, (short) (ultimo + 1)));

        auditor.registrar(localId, usuario.usuarioId(), "CREAR_CATEGORIA", ENTIDAD, categoria.getId(), null,
                Map.of("nombre", categoria.getNombre()));
        return aDto(categoria, PlatosDeCategoria.NINGUNO);
    }

    /** Si el nombre es el mismo, no guarda ni audita. */
    @Transactional
    public CategoriaAdminDto renombrar(UsuarioAutenticado usuario, Long categoriaId, String nombre) {
        Long localId = localDe(usuario);
        CategoriaMenu categoria = buscarCategoria(categoriaId, localId);
        String anterior = categoria.getNombre();
        categoria.renombrar(nombre);
        if (!anterior.equals(categoria.getNombre())) {
            categorias.guardar(categoria);
            auditor.registrar(localId, usuario.usuarioId(), "EDITAR_CATEGORIA", ENTIDAD, categoria.getId(),
                    Map.of("nombre", anterior), Map.of("nombre", categoria.getNombre()));
        }
        return aDto(categoria, platos.contarPorCategoria(localId).getOrDefault(categoria.getId(),
                PlatosDeCategoria.NINGUNO));
    }

    /**
     * El orden nuevo de todas de una vez, como quedan en P-14 al arrastrar (propuesto el 29/10). Tiene que traer
     * exactamente las del local: si falta una, sobra una, se repite o es de otro local, 400. Si el orden no
     * cambia, no guarda ni audita.
     */
    @Transactional
    public void ordenar(UsuarioAutenticado usuario, List<Long> ids) {
        Long localId = localDe(usuario);
        List<CategoriaMenu> delLocal = categorias.listarDelLocal(localId);
        Map<Long, CategoriaMenu> porId = delLocal.stream()
                .collect(Collectors.toMap(CategoriaMenu::getId, Function.identity()));
        Set<Long> pedidas = new HashSet<>(ids);
        if (pedidas.size() != ids.size() || !pedidas.equals(porId.keySet())) {
            throw new SolicitudInvalidaException(ORDEN_INCOMPLETO);
        }

        List<Long> antes = delLocal.stream().map(CategoriaMenu::getId).toList();
        boolean cambio = false;
        for (int i = 0; i < ids.size(); i++) {
            CategoriaMenu categoria = porId.get(ids.get(i));
            short orden = (short) (i + 1);
            if (categoria.getOrden() != orden) {
                categoria.moverA(orden);
                categorias.guardar(categoria);
                cambio = true;
            }
        }
        if (cambio) {
            auditor.registrar(localId, usuario.usuarioId(), "ORDENAR_CATEGORIAS", ENTIDAD, null,
                    Map.of("ids", antes), Map.of("ids", List.copyOf(ids)));
        }
    }

    /** Con platos, también fuera de carta, 409 con cuántos son (decidido el 29/10). */
    @Transactional
    public void borrar(UsuarioAutenticado usuario, Long categoriaId) {
        Long localId = localDe(usuario);
        CategoriaMenu categoria = buscarCategoria(categoriaId, localId);
        PlatosDeCategoria suyos = platos.contarPorCategoria(localId)
                .getOrDefault(categoria.getId(), PlatosDeCategoria.NINGUNO);
        if (suyos.hayAlguno()) {
            throw new CategoriaConPlatosException(suyos);
        }
        categorias.borrar(categoria);
        auditor.registrar(localId, usuario.usuarioId(), "BORRAR_CATEGORIA", ENTIDAD, categoria.getId(),
                Map.of("nombre", categoria.getNombre()), null);
    }

    private CategoriaMenu buscarCategoria(Long categoriaId, Long localId) {
        return categorias.buscarPorId(categoriaId, localId)
                .orElseThrow(() -> new NoEncontradoException("La categoría no existe"));
    }

    private static CategoriaAdminDto aDto(CategoriaMenu categoria, PlatosDeCategoria suyos) {
        return new CategoriaAdminDto(categoria.getId(), categoria.getNombre(), suyos.total(), suyos.fueraDeCarta());
    }

    /** El Gerente/Dueño no tiene local fijo y no gestiona la carta. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
