package com.chitgo.usuarios.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.PasarelaPush;
import com.chitgo.usuarios.dominio.puertos.SuscripcionesPush;

@Service
public class SuscribirPushService {
    private final SuscripcionesPush suscripciones;
    private final PasarelaPush pasarela;
    private final Reloj reloj;

    public SuscribirPushService(SuscripcionesPush suscripciones, PasarelaPush pasarela, Reloj reloj) {
        this.suscripciones = suscripciones;
        this.pasarela = pasarela;
        this.reloj = reloj;
    }

    public Map<String, Object> configuracion() {
        return Map.of("habilitado", pasarela.habilitada(), "clavePublica", pasarela.clavePublica());
    }

    @Transactional
    public void registrar(UsuarioAutenticado usuario, String endpoint, String p256dh, String auth) {
        exigirLocal(usuario);
        if (!pasarela.habilitada()) {
            throw new ReglaDeNegocioException("Las notificaciones Push no están configuradas.");
        }
        pasarela.validar(endpoint, p256dh, auth);
        suscripciones.registrar(new SuscripcionPush(usuario.localId(), usuario.usuarioId(), endpoint,
                hash(endpoint), p256dh, auth, reloj.ahora()));
    }

    @Transactional
    public void eliminar(UsuarioAutenticado usuario, String endpoint) {
        exigirLocal(usuario);
        suscripciones.eliminar(hash(endpoint), usuario.usuarioId(), usuario.localId());
    }

    private static void exigirLocal(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
    }

    private static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
