package com.chitgo.usuarios.dominio;

import java.time.Instant;
import jakarta.persistence.*;

/** Una suscripción del navegador tiene un solo destinatario, incluso en un celular compartido. */
@Entity
@Table(name = "suscripcion_push")
public class SuscripcionPush {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long localId;
    private Long usuarioId;
    @Column(length = 2048)
    private String endpoint;
    @Column(length = 64)
    private String endpointHash;
    @Column(length = 128)
    private String p256dh;
    @Column(length = 32)
    private String auth;
    private Instant actualizadaEn;

    protected SuscripcionPush() {}

    public SuscripcionPush(Long localId, Long usuarioId, String endpoint, String endpointHash,
            String p256dh, String auth, Instant ahora) {
        this.localId = localId;
        this.usuarioId = usuarioId;
        this.endpoint = endpoint;
        this.endpointHash = endpointHash;
        this.p256dh = p256dh;
        this.auth = auth;
        this.actualizadaEn = ahora;
    }

    public Long getId() { return id; }
    public Long getLocalId() { return localId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getEndpoint() { return endpoint; }
    public String getEndpointHash() { return endpointHash; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
