package com.chitgo.pedidos.dominio;

/** Por qué Caja tiene que confirmar el cierre de una cuenta (HU-10). */
public enum MotivoDeConfirmacion {

    NADIE_PIDIO_LA_CUENTA("Esta cuenta no la pidió nadie. ¿La cerramos igual?"),

    PEDIDOS_SIN_SERVIR("Hay pedidos sin servir. ¿Estás seguro de que querés cerrar la cuenta?");

    private final String mensaje;

    MotivoDeConfirmacion(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
