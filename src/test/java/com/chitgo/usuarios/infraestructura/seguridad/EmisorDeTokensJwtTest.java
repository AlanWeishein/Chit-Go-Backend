package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.TokenLeido;

class EmisorDeTokensJwtTest {

    private static final String CLAVE = Base64.getEncoder().encodeToString(
            "clave-de-prueba-de-32-bytes-o-mas!!".getBytes(StandardCharsets.UTF_8));
    private static final String OTRA_CLAVE = Base64.getEncoder().encodeToString(
            "otra-clave-de-prueba-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8));
    private static final Instant INICIO = Instant.parse("2026-10-15T20:00:00Z");
    private static final Duration DIEZ_MINUTOS = Duration.ofMinutes(10);

    private final RelojDePrueba reloj = new RelojDePrueba(INICIO);
    private final EmisorDeTokensJwt emisor = new EmisorDeTokensJwt(CLAVE, reloj);

    private final DatosDeSesion juan = new DatosDeSesion(3L, "MOZO", 1L, false);

    @Test
    void emiteYLeeLosDatosDeLaSesion() {
        TokenLeido leido = emisor.leer(emisor.emitir(juan, DIEZ_MINUTOS));

        assertThat(leido.datos()).isEqualTo(juan);
        assertThat(leido.expiraEn()).isEqualTo(INICIO.plus(DIEZ_MINUTOS));
    }

    @Test
    void elGerenteDuenoViajaSinLocal() {
        DatosDeSesion dueno = new DatosDeSesion(1L, "GERENTE_DUENO", null, true);

        TokenLeido leido = emisor.leer(emisor.emitir(dueno, DIEZ_MINUTOS));

        assertThat(leido.datos()).isEqualTo(dueno);
        assertThat(leido.datos().localId()).isNull();
    }

    @Test
    void laDuracionSeEligeAlEmitir() {
        TokenLeido caja = emisor.leer(emisor.emitir(new DatosDeSesion(5L, "CAJA", 1L, false), Duration.ofMinutes(60)));

        assertThat(caja.expiraEn()).isEqualTo(INICIO.plus(Duration.ofMinutes(60)));
    }

    @Test
    void sigueValidoHastaElVencimiento() {
        String token = emisor.emitir(juan, DIEZ_MINUTOS);

        reloj.avanzar(DIEZ_MINUTOS.minusSeconds(1));

        assertThat(emisor.leer(token).datos()).isEqualTo(juan);
    }

    @Test
    void rechazaUnTokenVencido() {
        String token = emisor.emitir(juan, DIEZ_MINUTOS);

        reloj.avanzar(DIEZ_MINUTOS.plusSeconds(1));

        assertThatThrownBy(() -> emisor.leer(token)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnTokenAlQueLeCambiaronElRol() {
        String[] partes = emisor.emitir(juan, DIEZ_MINUTOS).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String payloadAdmin = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"MOZO\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
        assertThat(payloadAdmin).isNotEqualTo(partes[1]);

        String alterado = partes[0] + "." + payloadAdmin + "." + partes[2];

        assertThatThrownBy(() -> emisor.leer(alterado)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnaFirmaAlterada() {
        String[] partes = emisor.emitir(juan, DIEZ_MINUTOS).split("\\.");
        // Se cambia un carácter del medio: el último puede tener bits de relleno y decodificar igual.
        char[] firma = partes[2].toCharArray();
        int medio = firma.length / 2;
        firma[medio] = firma[medio] == 'A' ? 'B' : 'A';

        String alterado = partes[0] + "." + partes[1] + "." + new String(firma);

        assertThatThrownBy(() -> emisor.leer(alterado)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        String deOtroServidor = new EmisorDeTokensJwt(OTRA_CLAVE, reloj).emitir(juan, DIEZ_MINUTOS);

        assertThatThrownBy(() -> emisor.leer(deOtroServidor)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnTokenSinFirma() {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString(
                "{\"sub\":\"2\",\"rol\":\"ADMIN\",\"localId\":1,\"debeCambiarCredencial\":false,\"exp\":4102444800}"
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> emisor.leer(cabecera + "." + payload + "."))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaCualquierCosaQueNoSeaUnToken() {
        assertThatThrownBy(() -> emisor.leer("no-es-un-token")).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> emisor.leer("")).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> emisor.leer(null)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void noArrancaConUnaClaveCorta() {
        String corta = Base64.getEncoder().encodeToString("corta".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new EmisorDeTokensJwt(corta, reloj)).isInstanceOf(WeakKeyException.class);
    }

    @Test
    void sinClaveConfiguradaUsaUnaAlAzar() {
        EmisorDeTokensJwt sinClave = new EmisorDeTokensJwt("", reloj);
        String token = sinClave.emitir(juan, DIEZ_MINUTOS);

        assertThat(sinClave.leer(token).datos()).isEqualTo(juan);
        assertThatThrownBy(() -> new EmisorDeTokensJwt("", reloj).leer(token))
                .isInstanceOf(TokenInvalidoException.class);
    }
}
