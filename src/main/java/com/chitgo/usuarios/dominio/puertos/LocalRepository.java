package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Local;

public interface LocalRepository {

    Optional<Local> buscarPorId(Long id);

    /** El token del QR de staff, que identifica al local antes del login (RF-13). */
    Optional<Local> buscarPorTokenStaff(String tokenStaff);
}
