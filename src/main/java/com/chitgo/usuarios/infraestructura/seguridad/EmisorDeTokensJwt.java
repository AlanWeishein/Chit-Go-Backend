package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.TokenLeido;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;

/**
 * JWT firmado con HS256. El usuario va en el subject; el rol, el local y debeCambiarCredencial, como claims.
 * La hora sale del Reloj, así un test puede vencer el token sin esperar.
 */
@Component
public class EmisorDeTokensJwt implements EmisorDeTokens {

    private static final Logger log = LoggerFactory.getLogger(EmisorDeTokensJwt.class);

    private static final String ROL = "rol";
    private static final String LOCAL_ID = "localId";
    private static final String DEBE_CAMBIAR_CREDENCIAL = "debeCambiarCredencial";

    private final SecretKey clave;
    private final Reloj reloj;

    /** @param claveBase64 la variable JWT_CLAVE: 32 bytes o más, en Base64 */
    public EmisorDeTokensJwt(@Value("${chitgo.jwt.clave:}") String claveBase64, Reloj reloj) {
        this.clave = armarClave(claveBase64);
        this.reloj = reloj;
    }

    @Override
    public String emitir(DatosDeSesion datos, Duration duracion) {
        Instant ahora = reloj.ahora();
        return Jwts.builder()
                .subject(datos.usuarioId().toString())
                .claim(ROL, datos.rol())
                .claim(LOCAL_ID, datos.localId())
                .claim(DEBE_CAMBIAR_CREDENCIAL, datos.debeCambiarCredencial())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public TokenLeido leer(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(clave)
                    .clock(() -> Date.from(reloj.ahora()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Token inválido o vencido", e);
        }

        String rol = claims.get(ROL, String.class);
        Boolean debeCambiarCredencial = claims.get(DEBE_CAMBIAR_CREDENCIAL, Boolean.class);
        if (claims.getSubject() == null || rol == null || debeCambiarCredencial == null || claims.getExpiration() == null) {
            throw new TokenInvalidoException("Al token le faltan datos");
        }
        try {
            DatosDeSesion datos = new DatosDeSesion(
                    Long.valueOf(claims.getSubject()), rol, claims.get(LOCAL_ID, Long.class), debeCambiarCredencial);
            return new TokenLeido(datos, claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Al token le faltan datos", e);
        }
    }

    private static SecretKey armarClave(String claveBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            log.warn("JWT_CLAVE no está definida: se usa una clave al azar y las sesiones se pierden al reiniciar el back.");
            return Jwts.SIG.HS256.key().build();
        }
        // Si la clave tiene menos de 32 bytes, hmacShaKeyFor tira WeakKeyException y el back no arranca.
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(claveBase64));
    }
}
