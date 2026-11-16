package com.chitgo.usuarios.dominio.puertos;

import com.chitgo.usuarios.dominio.SuscripcionPush;

public interface PasarelaPush {
    boolean habilitada();
    String clavePublica();
    void validar(String endpoint, String p256dh, String auth);
    int enviar(SuscripcionPush suscripcion, String mensaje) throws Exception;
}
