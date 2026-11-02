package com.chitgo.menu.dominio.puertos;

import java.util.Collection;
import java.util.List;

import com.chitgo.menu.dominio.Etiqueta;

public interface EtiquetaRepository {

    /** Las del local con esos ids. Las que no existen o son de otro local no vienen. */
    List<Etiqueta> buscarPorIds(Long localId, Collection<Long> ids);
}
