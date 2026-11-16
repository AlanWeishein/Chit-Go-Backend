package com.chitgo.menu.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Agrupa los platos de la carta de un local. */
@Entity
@Table(name = "categoria_menu")
public class CategoriaMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private String nombre;

    private short orden;

    protected CategoriaMenu() {
    }

    public CategoriaMenu(Long localId, String nombre, short orden) {
        this.localId = localId;
        this.nombre = nombre;
        this.orden = orden;
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

    public short getOrden() {
        return orden;
    }
}
