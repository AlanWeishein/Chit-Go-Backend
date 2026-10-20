package com.chitgo.menu.dominio;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** La carta de un local: destacados arriba y después las categorías en su orden. Solo platos disponibles. */
public record Carta(List<PlatoDeCarta> destacados, List<CategoriaDeCarta> categorias) {

    public static Carta de(List<CategoriaDeCarta> categorias) {
        List<PlatoDeCarta> destacados = categorias.stream()
                .flatMap(c -> c.platos().stream())
                .filter(PlatoDeCarta::destacado)
                .toList();
        return new Carta(destacados, categorias);
    }

    /**
     * Con varias etiquetas exige todas: quien pide "Sin TACC" y "Sin lactosa" no puede comer un plato que
     * tenga una sola. Una categoría que se queda sin platos no aparece.
     */
    public Carta filtrarPorEtiquetas(Set<String> etiquetas) {
        if (etiquetas == null || etiquetas.isEmpty()) {
            return this;
        }
        Set<String> requeridas = etiquetas.stream().map(Etiquetas::normalizar).collect(Collectors.toSet());
        List<CategoriaDeCarta> filtradas = categorias.stream()
                .map(c -> new CategoriaDeCarta(c.id(), c.nombre(), c.orden(),
                        c.platos().stream().filter(p -> p.tieneTodas(requeridas)).toList()))
                .filter(c -> !c.platos().isEmpty())
                .toList();
        return de(filtradas);
    }
}
