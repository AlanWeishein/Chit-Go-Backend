package com.chitgo.menu.dominio;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Etiqueta puesta en un plato. */
@Entity
@Table(name = "plato_etiqueta")
public class PlatoEtiqueta {

    @EmbeddedId
    private PlatoEtiquetaId id;

    private Long localId;

    @MapsId("platoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Plato plato;

    @MapsId("etiquetaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Etiqueta etiqueta;

    protected PlatoEtiqueta() {
    }

    public PlatoEtiqueta(Long localId, Plato plato, Etiqueta etiqueta) {
        this.id = new PlatoEtiquetaId(plato.getId(), etiqueta.getId());
        this.localId = localId;
        this.plato = plato;
        this.etiqueta = etiqueta;
        plato.agregarEtiqueta(this);
    }

    public PlatoEtiquetaId getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Plato getPlato() {
        return plato;
    }

    public Etiqueta getEtiqueta() {
        return etiqueta;
    }
}
