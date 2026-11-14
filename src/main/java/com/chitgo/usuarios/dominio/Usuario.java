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

    private boolean contrasenaProvisoria = false;

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

    /** El mozo todavía no eligió la contraseña que le puso el Admin (HU-14, decidido el 20/10). */
    public boolean isContrasenaProvisoria() {
        return contrasenaProvisoria;
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

    /**
     * Eligió su credencial en el primer ingreso o después de un blanqueo (CU-12): se guarda y la marca de
     * cambio obligatorio se apaga. Un hash NULL deja el que tenía, como la contraseña del mozo que solo eligió
     * el PIN. Con una contraseña nueva, la provisoria también queda atrás.
     */
    public void elegirCredencial(String nuevoPinHash, String nuevoHashPassword) {
        if (nuevoPinHash != null) {
            pinHash = nuevoPinHash;
        }
        if (nuevoHashPassword != null) {
            hashPassword = nuevoHashPassword;
            contrasenaProvisoria = false;
        }
        debeCambiarCredencial = false;
    }

    /**
     * El Admin le blanquea el PIN (HU-14, CP-10-06): el nuevo es provisorio, los intentos vuelven a 0 y se levanta
     * el bloqueo. La contraseña provisoria queda como estaba: un blanqueo de PIN no le vuelve a pedir la
     * contraseña al mozo.
     */
    public void blanquearPin(String nuevoPinHash) {
        pinHash = nuevoPinHash;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        debeCambiarCredencial = true;
    }

    /**
     * Alta de un empleado (HU-14): las credenciales las puso el Admin, así que son provisorias. Solo el mozo
     * tiene contraseña provisoria.
     */
    public static Usuario nuevoEmpleado(Long localId, Rol rol, String nombre, String nombreUsuario,
            String hashPassword, String pinHash) {
        Usuario usuario = new Usuario(localId, rol, nombre, nombreUsuario, null, hashPassword, pinHash);
        usuario.debeCambiarCredencial = true;
        usuario.contrasenaProvisoria = rol.esMozo();
        return usuario;
    }

    /** Cambia el nombre y, si es mozo, su usuario. El rol y las credenciales no se tocan. */
    public void renombrar(String nuevoNombre, String nuevoNombreUsuario) {
        nombre = nuevoNombre;
        nombreUsuario = nuevoNombreUsuario;
    }

    /** Baja lógica. Devuelve false si ya estaba dado de baja, para que darlo de baja dos veces no cambie nada. */
    public boolean darDeBaja() {
        if (!activo) {
            return false;
        }
        activo = false;
        return true;
    }

    /** Entró con la credencial correcta: los intentos vuelven a 0. */
    public void registrarIngreso() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }
}
