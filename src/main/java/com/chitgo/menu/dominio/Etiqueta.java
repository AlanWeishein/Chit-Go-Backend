package com.chitgo.menu.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Etiqueta de alérgenos o dieta que define cada local (Sin TACC, Vegano...). */
@Entity
@Table(name = "etiqueta")
public class Etiqueta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private String nombre;

    protected Etiqueta() {
    }

    public Etiqueta(Long localId, String nombre) {
        this.localId = localId;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public String getNombre() {
        return nombre;
    }
}
