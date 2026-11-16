package com.chitgo.menu.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Etiqueta de alérgenos o dieta que define cada local (Sin TACC, Vegano...). No se repite dentro del local: lo
 * frena el UNIQUE (local_id, nombre), con el collation ai_ci, así "Sin TACC" y "sin tacc" son la misma.
 */
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

    /** Sin nombre, 400 "Poné un nombre". */
    public Etiqueta(Long localId, String nombre) {
        this.localId = localId;
        this.nombre = Nombres.obligatorio(nombre);
    }

    /** HU-12. Sin nombre, 400 y no cambia. Que no se repita lo controla el Service. */
    public void renombrar(String nombre) {
        this.nombre = Nombres.obligatorio(nombre);
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
