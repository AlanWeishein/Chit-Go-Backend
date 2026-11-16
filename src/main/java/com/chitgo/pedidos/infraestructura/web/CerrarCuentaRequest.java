package com.chitgo.pedidos.infraestructura.web;

import java.util.List;

/**
 * Body opcional del cierre. {@code pedidosACancelar} es lo que Caja vio al confirmar: el back lo compara con lo
 * que hay para frenar si apareció un pedido sin servir nuevo. Sin body, o con {@code confirmar} omitido, es false.
 */
public record CerrarCuentaRequest(Boolean confirmar, List<Long> pedidosACancelar) {

    public boolean confirma() {
        return Boolean.TRUE.equals(confirmar);
    }
}
