package com.chitgo.menu.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Agrupa los platos de la carta de un local. La carta las muestra por orden. */
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

    /** Sin nombre, 400 "Poné un nombre". */
    public CategoriaMenu(Long localId, String nombre, short orden) {
        this.localId = localId;
        this.nombre = Nombres.obligatorio(nombre);
        this.orden = orden;
    }

    /** HU-12. Sin nombre, 400 y no cambia. */
    public void renombrar(String nombre) {
        this.nombre = Nombres.obligatorio(nombre);
    }

    /** HU-12: el lugar en la carta, empezando por 1. */
    public void moverA(short orden) {
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
