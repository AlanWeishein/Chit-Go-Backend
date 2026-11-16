package com.chitgo.usuarios.dominio;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** Persona que trabaja en el local. Todo empleado tiene un Usuario. */
@Entity
@Table(name = "empleado")
public class Empleado {

    /** Los roles que el Admin da de alta, edita y da de baja (HU-14). El Admin lo crea el Gerente/Dueño. */
    public static final Set<String> ROLES_GESTIONABLES = Set.of("MOZO", "CAJA", "COCINA");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;

    @OneToMany(mappedBy = "empleado", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MozoMesa> mesasAsignadas = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Empleado() {
    }

    public Empleado(Long localId, Usuario usuario) {
        this.localId = localId;
        this.usuario = usuario;
    }

    /** Solo arma la relación: lo llama el constructor de MozoMesa. */
    void asignarMesa(MozoMesa mozoMesa) {
        mesasAsignadas.add(mozoMesa);
    }

    /**
     * Deja asignadas exactamente esas mesas: saca las que no están (orphanRemoval borra la fila) y suma las nuevas
     * con el local del empleado. Que sean mesas del local lo controla el Service.
     *
     * @return false si ya tenía esas mismas mesas
     */
    public boolean reemplazarMesas(Set<Long> mesaIds) {
        boolean cambio = mesasAsignadas.removeIf(mm -> !mesaIds.contains(mm.getMesaId()));
        Set<Long> actuales = getMesaIds();
        for (Long mesaId : mesaIds) {
            if (!actuales.contains(mesaId)) {
                new MozoMesa(localId, this, mesaId);
                cambio = true;
            }
        }
        return cambio;
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

    public Set<MozoMesa> getMesasAsignadas() {
        return Collections.unmodifiableSet(mesasAsignadas);
    }

    public Set<Long> getMesaIds() {
        return mesasAsignadas.stream().map(MozoMesa::getMesaId).collect(Collectors.toUnmodifiableSet());
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public boolean estaActivo() {
        return usuario.isActivo();
    }

    /** El nombre vive en el usuario. */
    public void renombrar(String nombre, String nombreUsuario) {
        usuario.renombrar(nombre, nombreUsuario);
    }

    /** Baja lógica: false si ya estaba dado de baja. */
    public boolean darDeBaja() {
        return usuario.darDeBaja();
    }
}
