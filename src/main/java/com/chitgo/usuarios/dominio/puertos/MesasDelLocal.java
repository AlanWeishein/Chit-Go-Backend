package com.chitgo.usuarios.dominio.puertos;

import java.util.Collection;
import java.util.Set;

/** Las mesas son de {@code pedidos}: {@code usuarios} solo pregunta cuáles son del local, para asignárselas a un mozo. */
public interface MesasDelLocal {

    /** De esos ids, los que son mesas del local. Uno que no existe o es de otro local no vuelve. */
    Set<Long> deEsteLocal(Collection<Long> mesaIds, Long localId);
}
