package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Local;

public interface LocalRepository {

    Optional<Local> buscarPorId(Long id);

    /** El token del QR de staff, que identifica al local antes del login (RF-13). */
    Optional<Local> buscarPorTokenStaff(String tokenStaff);

    Optional<Local> buscarPorTokenPantalla(String tokenPantalla);

    /**
     * El local del que es propietario el Gerente/Dueño (HU-16). En el MVP 1 tiene uno solo (10/10); si algún día
     * tiene varios, devuelve el de id más bajo, y eso hay que revisarlo en el MVP 2.
     */
    Optional<Local> buscarPorPropietario(Long usuarioId);

    /** El alta del local con el script (HU-16). Con flush, así el id y cualquier error salen acá. */
    Local guardar(Local local);
}
