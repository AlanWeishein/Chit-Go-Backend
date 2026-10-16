package com.chitgo.usuarios.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Quien entra al sistema. El rol es un dato, no una subclase. */
@Entity
@Table(name = "usuario")
public class Usuario {

    /** RNF-07: a los 5 intentos fallidos seguidos queda bloqueado. */
    public static final int INTENTOS_PARA_BLOQUEAR = 5;

    /**
     * El bloqueo por intentos no vence solo: dura hasta que el Admin blanquea el PIN (HU-14), que pone
     * bloqueadoHasta en NULL. Como la columna es "hasta", se guarda una fecha que no llega nunca. Es 2038
     * porque un TIMESTAMP de MySQL no pasa del 2038-01-19.
     */
    public static final Instant BLOQUEO_SIN_VENCIMIENTO = Instant.parse("2038-01-01T00:00:00Z");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Rol rol;

    private String nombre;

    private String nombreUsuario;

    private String email;

    private String hashPassword;

    private String pinHash;

    @JdbcTypeCode(SqlTypes.TINYINT)
    private short intentosFallidos = 0;

    private Instant bloqueadoHasta;

    private boolean debeCambiarCredencial = true;

    private boolean activo = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Usuario() {
    }

    public Usuario(Long localId, Rol rol, String nombre, String nombreUsuario, String email, String hashPassword, String pinHash) {
        this.localId = localId;
        this.rol = rol;
        this.nombre = nombre;
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.hashPassword = hashPassword;
        this.pinHash = pinHash;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Rol getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getEmail() {
        return email;
    }

    public String getHashPassword() {
        return hashPassword;
    }

    public String getPinHash() {
        return pinHash;
    }

    public short getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public boolean isDebeCambiarCredencial() {
        return debeCambiarCredencial;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /** Un intento fallido más. Al quinto seguido queda bloqueado sin vencimiento. */
    public void registrarIntentoFallido() {
        intentosFallidos++;
        if (intentosFallidos >= INTENTOS_PARA_BLOQUEAR) {
            bloqueadoHasta = BLOQUEO_SIN_VENCIMIENTO;
        }
    }

    /** Entró con la credencial correcta: los intentos vuelven a 0. */
    public void registrarIngreso() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }
}
