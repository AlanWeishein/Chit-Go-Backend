package com.chitgo.usuarios.infraestructura.push;

import static org.assertj.core.api.Assertions.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Set;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Utils;

class PasarelaWebPushTest {
    private static String publica;
    private static String privada;
    private static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    @BeforeAll
    static void claves() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generador = KeyPairGenerator.getInstance("ECDH", "BC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = generador.generateKeyPair();
        publica = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey) par.getPublic()));
        privada = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPrivateKey) par.getPrivate()));
    }

    private PushProperties config(boolean habilitado) {
        return new PushProperties(habilitado, publica, privada, "mailto:admin@example.com", Set.of("fcm.googleapis.com"));
    }

    @Test
    void cifraElPayloadYFirmaVapidSinEnviarNadaPorRed() throws Exception {
        var cifrador = new PasarelaWebPush.CifradorPush(config(true));
        var request = cifrador.preparar(new Notification("https://fcm.googleapis.com/fcm/send/token", publica, AUTH, "aviso"));
        assertThat(request.getHeaders()).containsEntry("Content-Encoding", "aes128gcm");
        assertThat(request.getHeaders().get("Authorization")).startsWith("vapid t=").contains(", k=" + publica);
        assertThat(request.getBody()).hasSizeGreaterThan(65);
        assertThat(new String(request.getBody(), java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("aviso");
    }

    @Test
    void aceptaUnaSuscripcionDeProveedorPermitidoConUnPuntoP256Valido() throws Exception {
        var pasarela = new PasarelaWebPush(config(true));
        try {
            pasarela.validar("https://fcm.googleapis.com/token", publica, AUTH);
            assertThat(pasarela.clavePublica()).isEqualTo(publica);
        } finally { pasarela.cerrar(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fcm.googleapis.com/token", "https://localhost/token", "https://127.0.0.1/token",
            "https://fcm.googleapis.com.evil.test/token", "https://user@fcm.googleapis.com/token",
            "https://fcm.googleapis.com:8080/token", "https://fcm.googleapis.com/token#fragment"})
    void rechazaEndpointsQuePermitirianSolicitudesArbitrarias(String endpoint) throws Exception {
        var pasarela = new PasarelaWebPush(config(false));
        try {
            assertThatThrownBy(() -> pasarela.validar(endpoint, publica, AUTH)).isInstanceOf(SolicitudInvalidaException.class);
        } finally { pasarela.cerrar(); }
    }

    @Test
    void rechazaClavesInvalidasAunqueElEndpointSeaValido() throws Exception {
        var pasarela = new PasarelaWebPush(config(false));
        try {
            assertThatThrownBy(() -> pasarela.validar("https://fcm.googleapis.com/token", "basura", AUTH))
                    .isInstanceOf(SolicitudInvalidaException.class);
            assertThatThrownBy(() -> pasarela.validar("https://fcm.googleapis.com/token", publica, "AA"))
                    .isInstanceOf(SolicitudInvalidaException.class);
        } finally { pasarela.cerrar(); }
    }
}
