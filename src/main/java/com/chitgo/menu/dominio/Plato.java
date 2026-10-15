package com.chitgo.menu.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** Plato de la carta. Con activo en false deja de ofrecerse sin borrarse. */
@Entity
@Table(name = "plato")
public class Plato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CategoriaMenu categoria;

    private String nombre;

    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precio;

    private String imagenUrl;

    private boolean destacado = false;

    private boolean activo = true;

    @ManyToMany
    @JoinTable(name = "plato_etiqueta",
            joinColumns = @JoinColumn(name = "plato_id"),
            inverseJoinColumns = @JoinColumn(name = "etiqueta_id"))
    private Set<Etiqueta> etiquetas = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Plato() {
    }

    public Plato(Long localId, CategoriaMenu categoria, String nombre, String descripcion, BigDecimal precio, String imagenUrl) {
        this.localId = localId;
        this.categoria = categoria;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.imagenUrl = imagenUrl;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public CategoriaMenu getCategoria() {
        return categoria;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public boolean isDestacado() {
        return destacado;
    }

    public boolean isActivo() {
        return activo;
    }

    public Set<Etiqueta> getEtiquetas() {
        return Collections.unmodifiableSet(etiquetas);
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
