package com.chitgo.menu.dominio;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/** Clave de PlatoEtiqueta: el plato y la etiqueta. */
@Embeddable
public class PlatoEtiquetaId implements Serializable {

    private Long platoId;

    private Long etiquetaId;

    protected PlatoEtiquetaId() {
    }

    PlatoEtiquetaId(Long platoId, Long etiquetaId) {
        this.platoId = platoId;
        this.etiquetaId = etiquetaId;
    }

    public Long getPlatoId() {
        return platoId;
    }

    public Long getEtiquetaId() {
        return etiquetaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlatoEtiquetaId otro)) {
            return false;
        }
        return Objects.equals(platoId, otro.platoId) && Objects.equals(etiquetaId, otro.etiquetaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platoId, etiquetaId);
    }
}
