package com.chitgo.usuarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Restaurante. Su propietario es el Gerente/Dueño (RF-29). */
@Entity
@Table(name = "local")
public class Local {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long propietarioUsuarioId;

    private String nombre;

    private String direccion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String tokenStaff;

    private short minutosInactividadSesion = 10;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Local() {
    }

    public Local(Long propietarioUsuarioId, String nombre, String direccion, String tokenStaff) {
        this.propietarioUsuarioId = propietarioUsuarioId;
        this.nombre = nombre;
        this.direccion = direccion;
        this.tokenStaff = tokenStaff;
    }

    public Long getId() {
        return id;
    }

    public Long getPropietarioUsuarioId() {
        return propietarioUsuarioId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTokenStaff() {
        return tokenStaff;
    }

    public short getMinutosInactividadSesion() {
        return minutosInactividadSesion;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
