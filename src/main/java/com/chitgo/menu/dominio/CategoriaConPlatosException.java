package com.chitgo.menu.dominio;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;

/**
 * HU-12: una categoría con platos no se borra, tampoco si están todos fuera de carta (la FK plato.categoria_id no
 * deja). Responde 409. Decidido el 29/10.
 */
public class CategoriaConPlatosException extends ReglaDeNegocioException {

    static final String MOVELOS = "Movelos a otra antes de borrarla";

    /** "La categoría tiene 3 platos (1 fuera de carta). Movelos a otra antes de borrarla". */
    public CategoriaConPlatosException(PlatosDeCategoria platos) {
        super("La categoría tiene " + platos.total() + (platos.total() == 1 ? " plato" : " platos")
                + (platos.fueraDeCarta() > 0 ? " (" + platos.fueraDeCarta() + " fuera de carta)" : "") + ". " + MOVELOS);
    }

    /** Le metieron un plato justo mientras se borraba: lo frenó la FK y no se sabe cuántos son. */
    public CategoriaConPlatosException() {
        super("La categoría tiene platos. " + MOVELOS);
    }
}
