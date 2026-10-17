package com.chitgo.usuarios.dominio;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/** GERENTE_DUENO, ADMIN, MOZO, CAJA o COCINA. Datos fijos. */
@Entity
@Table(name = "rol")
public class Rol {

    /** Los que entran con PIN desde la lista del QR de staff (RF-32). */
    public static final Set<String> CODIGOS_CON_PIN = Set.of("MOZO", "CAJA", "COCINA");

    /** Los que entran con email y contraseña (RF-32, P-12). */
    public static final Set<String> CODIGOS_CON_EMAIL = Set.of("ADMIN", "GERENTE_DUENO");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;

    private String nombre;

    @ManyToMany
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new HashSet<>();

    protected Rol() {
    }

    public Rol(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Set<Permiso> getPermisos() {
        return Collections.unmodifiableSet(permisos);
    }

    public boolean entraConPin() {
        return CODIGOS_CON_PIN.contains(codigo);
    }

    public boolean entraConEmail() {
        return CODIGOS_CON_EMAIL.contains(codigo);
    }

    /** El mozo además entra con usuario y contraseña cada 7 días (RF-13). */
    public boolean esMozo() {
        return "MOZO".equals(codigo);
    }
}
