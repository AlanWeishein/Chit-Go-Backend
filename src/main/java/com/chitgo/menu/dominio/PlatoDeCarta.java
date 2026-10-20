package com.chitgo.menu.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Un plato tal como lo ve el cliente en la carta. */
public record PlatoDeCarta(Long id, String nombre, String descripcion, BigDecimal precio, String imagenUrl,
                           boolean destacado, List<String> etiquetas) {

    public boolean tieneTodas(Set<String> requeridasNormalizadas) {
        Set<String> mias = etiquetas.stream().map(Etiquetas::normalizar).collect(Collectors.toSet());
        return mias.containsAll(requeridasNormalizadas);
    }
}
