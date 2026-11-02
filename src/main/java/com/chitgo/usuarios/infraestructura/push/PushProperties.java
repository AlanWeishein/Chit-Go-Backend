package com.chitgo.usuarios.infraestructura.push;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chitgo.push")
public record PushProperties(boolean habilitado, String clavePublica, String clavePrivada,
                             String contacto, Set<String> hostsPermitidos) {
    public PushProperties {
        clavePublica = clavePublica == null ? "" : clavePublica;
        clavePrivada = clavePrivada == null ? "" : clavePrivada;
        contacto = contacto == null ? "" : contacto;
        hostsPermitidos = hostsPermitidos == null ? Set.of() : Set.copyOf(hostsPermitidos);
        if (habilitado && (clavePublica.isBlank() || clavePrivada.isBlank() || contacto.isBlank())) {
            throw new IllegalArgumentException("Push necesita clave pública, clave privada y contacto VAPID.");
        }
    }
}
