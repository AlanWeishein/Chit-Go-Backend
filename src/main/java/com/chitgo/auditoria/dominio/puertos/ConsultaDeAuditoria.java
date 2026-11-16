package com.chitgo.auditoria.dominio.puertos;

import java.time.Instant;
import com.chitgo.compartido.dominio.PaginaDe;

/** Solo lectura, sin operaciones de edición o borrado de registros. */
public interface ConsultaDeAuditoria {
    PaginaDe<Fila> consultar(Long localId, String entidad, String accion, Long usuarioId, Instant desde,
            Instant hasta, int page, int size);

    record Fila(Long id, Long usuarioId, String usuario, String accion, String entidad, Long entidadId,
            Object valorAnterior, Object valorNuevo, Instant creadoEn) { }
}
