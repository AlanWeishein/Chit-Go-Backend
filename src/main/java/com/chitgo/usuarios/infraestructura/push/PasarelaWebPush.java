package com.chitgo.usuarios.infraestructura.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Security;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import jakarta.annotation.PreDestroy;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.PasarelaPush;

/** VAPID y cifrado aes128gcm. Las llamadas HTTP tienen plazo y no siguen redirecciones. */
@Component
class PasarelaWebPush implements PasarelaPush {
    private final PushProperties config;
    private final CifradorPush push;
    private final HttpClient http;

    // La biblioteca resuelve VAPID y el cifrado; el transporte HTTP lo provee Java 21.
    static class CifradorPush extends AbstractPushService<CifradorPush> {
        CifradorPush(PushProperties config) throws java.security.GeneralSecurityException {
            super(config.clavePublica(), config.clavePrivada(), config.contacto());
        }

        nl.martijndwars.webpush.HttpRequest preparar(Notification notificacion) throws Exception {
            return prepareRequest(notificacion, Encoding.AES128GCM);
        }
    }

    PasarelaWebPush(PushProperties config) throws Exception {
        this.config = config;
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.push = config.habilitado()
                ? new CifradorPush(config) : null;
        if (push != null && !Utils.verifyKeyPair(push.getPrivateKey(), push.getPublicKey())) {
            throw new IllegalArgumentException("Las claves VAPID no forman un par.");
        }
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public boolean habilitada() { return config.habilitado(); }

    @Override
    public String clavePublica() { return habilitada() ? config.clavePublica() : ""; }

    @Override
    public void validar(String endpoint, String p256dh, String auth) {
        try {
            URI uri = URI.create(endpoint);
            // La suscripción viene del navegador: no permite que el servidor haga POST a una URL arbitraria.
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !config.hostsPermitidos().contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException();
            }
            byte[] clave = Base64.getUrlDecoder().decode(p256dh);
            if (clave.length != 65 || clave[0] != 4 || Base64.getUrlDecoder().decode(auth).length != 16) {
                throw new IllegalArgumentException();
            }
            Utils.loadPublicKey(clave); // Además del largo, comprueba que sea un punto válido de P-256.
        } catch (Exception e) {
            throw new SolicitudInvalidaException("La suscripción Push no es válida.");
        }
    }

    @Override
    public int enviar(SuscripcionPush suscripcion, String mensaje) throws Exception {
        if (!habilitada()) {
            return 503;
        }
        validar(suscripcion.getEndpoint(), suscripcion.getP256dh(), suscripcion.getAuth());
        Notification notificacion = new Notification(suscripcion.getEndpoint(), suscripcion.getP256dh(),
                suscripcion.getAuth(), mensaje.getBytes(java.nio.charset.StandardCharsets.UTF_8), 120);
        var cifrado = push.preparar(notificacion);
        var request = HttpRequest.newBuilder(URI.create(cifrado.getUrl())).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofByteArray(cifrado.getBody()));
        cifrado.getHeaders().forEach(request::header);
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @PreDestroy
    public void cerrar() { http.close(); }
}
