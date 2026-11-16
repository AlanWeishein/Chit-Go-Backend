package com.chitgo.usuarios.infraestructura.seguridad;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeLimiteDeLogin.class)
class ConfiguracionDeLimiteDeLogin {
}
