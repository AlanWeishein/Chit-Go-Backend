package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.DispositivoHabilitado;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * JWT firmado con HS256 y la misma JWT_CLAVE que el de sesión. El mozo va en el subject, y el audience
 * "dispositivo" es lo que lo separa del token de sesión: este lector exige el audience, y el de sesión exige
 * el rol, que acá no está.
 */
@Component
public class EmisorDeHabilitacionesJwt implements EmisorDeHabilitaciones {

    static final String AUDIENCIA = "dispositivo";

    private final SecretKey clave;
    private final Reloj reloj;

    /** @param claveBase64 la variable JWT_CLAVE, la misma del token de sesión */
    public EmisorDeHabilitacionesJwt(@Value("${chitgo.jwt.clave:}") String claveBase64, Reloj reloj) {
        this.clave = EmisorDeTokensJwt.armarClave(claveBase64);
        this.reloj = reloj;
    }

    @Override
    public String emitir(Long usuarioId) {
        Instant ahora = reloj.ahora();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .audience().add(AUDIENCIA).and()
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(DispositivoHabilitado.VIGENCIA)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public DispositivoHabilitado leer(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(clave)
                    .requireAudience(AUDIENCIA)
                    .clock(() -> Date.from(reloj.ahora()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("Habilitación inválida o vencida", e);
        }

        if (claims.getSubject() == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new TokenInvalidoException("A la habilitación le faltan datos");
        }
        try {
            return new DispositivoHabilitado(Long.valueOf(claims.getSubject()), claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant());
        } catch (NumberFormatException e) {
            throw new TokenInvalidoException("A la habilitación le faltan datos", e);
        }
    }
}
