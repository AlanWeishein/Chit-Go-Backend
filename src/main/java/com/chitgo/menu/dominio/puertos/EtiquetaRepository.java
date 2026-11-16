package com.chitgo.menu.dominio.puertos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.chitgo.menu.dominio.Etiqueta;

public interface EtiquetaRepository {

    /** Si ya hay una con ese nombre en el local, lo frena el UNIQUE y tira EtiquetaRepetidaException. */
    Etiqueta guardar(Etiqueta etiqueta);

    /** Vacío si no existe o es de otro local (RNF-10). */
    Optional<Etiqueta> buscarPorId(Long id, Long localId);

    /** Las del local con esos ids. Las que no existen o son de otro local no vienen. */
    List<Etiqueta> buscarPorIds(Long localId, Collection<Long> ids);

    /** Las del local por nombre. */
    List<Etiqueta> listarDelLocal(Long localId);

    /**
     * Si otra etiqueta del local ya se llama así. Compara la base, con el collation ai_ci: "sin tacc" encuentra
     * a "Sin TACC".
     *
     * @param salvoId la que se está renombrando, que no cuenta; NULL en un alta
     */
    boolean existeConNombre(Long localId, String nombre, Long salvoId);

    /** Se va también de los platos que la tenían: plato_etiqueta borra en cascada. */
    void borrar(Etiqueta etiqueta);
}
