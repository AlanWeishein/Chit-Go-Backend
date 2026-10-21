package com.chitgo.menu.dominio;

import java.util.List;

public record CategoriaDeCarta(Long id, String nombre, int orden, List<PlatoDeCarta> platos) {
}
