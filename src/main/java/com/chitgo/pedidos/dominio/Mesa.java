package com.chitgo.pedidos.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mesa del local. No guarda estado: se deriva de su Cuenta. */
@Entity
@Table(name = "mesa")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private String numero;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String tokenQr;

    private boolean inhabilitada = false;

    private boolean activa = true;

    private String motivoInhabilitacion;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Mesa() {
    }

    public Mesa(Long localId, String numero, String tokenQr) {
        this.localId = localId;
        renombrar(numero);
        this.tokenQr = tokenQr;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public String getNumero() {
        return numero;
    }

    public String getTokenQr() {
        return tokenQr;
    }

    public boolean isInhabilitada() {
        return inhabilitada;
    }

    public boolean isActiva() { return activa; }

    public String getMotivoInhabilitacion() { return motivoInhabilitacion; }

    public void renombrar(String numero) {
        if (numero == null || numero.isBlank() || numero.strip().length() > 20) {
            throw new SolicitudInvalidaException("Elegí un número de mesa de hasta 20 caracteres");
        }
        this.numero = numero.strip();
    }

    public void cambiarDisponibilidad(boolean inhabilitada, String motivo) {
        if (inhabilitada && (motivo == null || motivo.isBlank() || motivo.strip().length() > 255)) {
            throw new SolicitudInvalidaException("Indicá el motivo de inhabilitación (hasta 255 caracteres)");
        }
        this.inhabilitada = inhabilitada;
        this.motivoInhabilitacion = inhabilitada ? motivo.strip() : null;
    }

    public void darDeBaja() { activa = false; }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
