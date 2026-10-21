package com.chitgo.compartido.dominio;

/** Otro request cambió el mismo registro un instante antes (optimistic locking, RNF-01). Responde 409. */
public class ConflictoDeConcurrenciaException extends ReglaDeNegocioException {

    public ConflictoDeConcurrenciaException(String mensaje) {
        super(mensaje);
    }
}
