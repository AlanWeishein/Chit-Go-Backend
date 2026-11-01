package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.SuscripcionesPush;

@Repository
@Transactional
class SuscripcionesPushAdapter implements SuscripcionesPush {
    private final SuscripcionPushJpaRepository jpa;

    SuscripcionesPushAdapter(SuscripcionPushJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public void registrar(SuscripcionPush s) {
        jpa.registrar(s.getLocalId(), s.getUsuarioId(), s.getEndpoint(), s.getEndpointHash(),
                s.getP256dh(), s.getAuth(), s.getActualizadaEn());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuscripcionPush> delUsuario(Long usuarioId, Long localId) {
        return jpa.findByUsuarioIdAndLocalId(usuarioId, localId);
    }

    @Override
    public void eliminar(String hash, Long usuarioId, Long localId) {
        jpa.deleteByEndpointHashAndUsuarioIdAndLocalId(hash, usuarioId, localId);
    }
}
