package com.chitgo.usuarios.dominio.puertos;

import java.util.List;
import com.chitgo.usuarios.dominio.SuscripcionPush;

public interface SuscripcionesPush {
    void registrar(SuscripcionPush suscripcion);

    List<SuscripcionPush> delUsuario(Long usuarioId, Long localId);

    void eliminar(String endpointHash, Long usuarioId, Long localId);
}
