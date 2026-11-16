package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cupos técnicos de login, separados del contador de fallos de RNF-07. */
@ConfigurationProperties("chitgo.seguridad.login-email")
public record PropiedadesDeLimiteDeLogin(
        @DefaultValue("3") int solicitudes,
        @DefaultValue("60s") Duration ventana,
        @DefaultValue("10000") int maximoDeClaves) {

    public PropiedadesDeLimiteDeLogin {
        if (solicitudes < 1 || maximoDeClaves < 1 || ventana == null
                || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("Los límites de chitgo.seguridad.login-email deben ser positivos");
        }
    }
}
