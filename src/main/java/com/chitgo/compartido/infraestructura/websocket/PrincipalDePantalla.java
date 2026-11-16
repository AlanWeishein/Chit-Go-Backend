package com.chitgo.compartido.infraestructura.websocket;

import java.security.Principal;

/** Acceso de solo lectura a un único topic; no es una sesión de personal. */
record PrincipalDePantalla(Long localId) implements Principal {
    @Override public String getName() { return "pantalla:" + localId; }
}
