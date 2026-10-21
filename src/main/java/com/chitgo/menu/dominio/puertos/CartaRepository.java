package com.chitgo.menu.dominio.puertos;

import java.util.Collection;
import java.util.List;

import com.chitgo.menu.dominio.Plato;

public interface CartaRepository {

    /** Los platos disponibles (activo) del local, con su categoría y sus etiquetas ya cargadas. */
    List<Plato> listarActivosDelLocal(Long localId);

    /** Los platos del local con esos ids, estén o no disponibles: sirve para el precio y para los nombres. */
    List<Plato> buscarPorIds(Long localId, Collection<Long> ids);
}
