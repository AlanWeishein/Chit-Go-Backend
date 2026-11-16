package com.chitgo.auditoria.infraestructura.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.auditoria.dominio.puertos.ConsultaDeAuditoria;
import com.chitgo.compartido.dominio.PaginaDe;

@Repository
class ConsultaDeAuditoriaJdbc implements ConsultaDeAuditoria {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    ConsultaDeAuditoriaJdbc(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override
    public PaginaDe<Fila> consultar(Long localId, String entidad, String accion, Long usuarioId, Instant desde,
            Instant hasta, int page, int size) {
        var parametros = new MapSqlParameterSource("local", localId).addValue("limite", size).addValue("offset", (long) page * size);
        StringBuilder filtro = new StringBuilder(" FROM registro_auditoria a JOIN usuario u ON u.id=a.usuario_id WHERE a.local_id=:local");
        if (entidad != null) { filtro.append(" AND a.entidad=:entidad"); parametros.addValue("entidad", entidad); }
        if (accion != null) { filtro.append(" AND a.accion=:accion"); parametros.addValue("accion", accion); }
        if (usuarioId != null) { filtro.append(" AND a.usuario_id=:usuario"); parametros.addValue("usuario", usuarioId); }
        if (desde != null) { filtro.append(" AND a.creado_en>=:desde"); parametros.addValue("desde", Timestamp.from(desde)); }
        if (hasta != null) { filtro.append(" AND a.creado_en<:hasta"); parametros.addValue("hasta", Timestamp.from(hasta)); }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + filtro, parametros, Long.class);
        var filas = jdbc.query("SELECT a.*,u.nombre usuario" + filtro + " ORDER BY a.creado_en DESC,a.id DESC LIMIT :limite OFFSET :offset",
                parametros, (r, n) -> new Fila(r.getLong("id"), r.getLong("usuario_id"), r.getString("usuario"),
                        r.getString("accion"), r.getString("entidad"), r.getObject("entidad_id", Long.class),
                        valor(r.getString("valor_anterior")), valor(r.getString("valor_nuevo")), r.getTimestamp("creado_en").toInstant()));
        return new PaginaDe<>(filas, total);
    }

    private Object valor(String texto) { return texto == null ? null : json.readValue(texto, Object.class); }
}
