package com.chitgo.usuarios.dominio;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** Persona que trabaja en el local. Todo empleado tiene un Usuario. */
@Entity
@Table(name = "empleado")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;

    @ElementCollection
    @CollectionTable(name = "mozo_mesa", joinColumns = @JoinColumn(name = "empleado_id"))
    @Column(name = "mesa_id")
    private Set<Long> mesaIds = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Empleado() {
    }

    public Empleado(Long localId, Usuario usuario) {
        this.localId = localId;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Set<Long> getMesaIds() {
        return Collections.unmodifiableSet(mesaIds);
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
