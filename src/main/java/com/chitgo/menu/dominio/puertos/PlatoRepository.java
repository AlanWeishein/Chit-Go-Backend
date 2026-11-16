package com.chitgo.menu.dominio.puertos;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.PlatosDeCategoria;

/** Los platos que gestiona el Admin (HU-12). La carta del cliente sigue en CartaRepository. */
public interface PlatoRepository {

    Plato guardar(Plato plato);

    /** Vacío si no existe o es de otro local: para el que pregunta es lo mismo (RNF-10). */
    Optional<Plato> buscarPorId(Long id, Long localId);

    /** Todos los del local, también los fuera de carta, por el orden de la categoría y después por nombre. */
    List<Plato> listarDelLocal(Long localId);

    /** Cuántos platos tiene cada categoría del local, también los fuera de carta. Las que no tienen no vienen. */
    Map<Long, PlatosDeCategoria> contarPorCategoria(Long localId);
}
