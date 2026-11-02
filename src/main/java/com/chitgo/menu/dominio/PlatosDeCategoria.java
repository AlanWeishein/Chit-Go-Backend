package com.chitgo.menu.dominio;

/**
 * Cuántos platos tiene una categoría, contando los fuera de carta.
 *
 * @param fueraDeCarta los que tienen activo en false (09/10)
 */
public record PlatosDeCategoria(long total, long fueraDeCarta) {

    public static final PlatosDeCategoria NINGUNO = new PlatosDeCategoria(0, 0);

    public boolean hayAlguno() {
        return total > 0;
    }
}
