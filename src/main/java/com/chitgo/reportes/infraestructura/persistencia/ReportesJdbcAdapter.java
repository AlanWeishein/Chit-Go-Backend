package com.chitgo.reportes.infraestructura.persistencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.chitgo.compartido.dominio.PaginaDe;
import com.chitgo.reportes.dominio.puertos.ReportesRepository;

/** Proyecciones agregadas en SQL; no carga entidades ni hace una consulta por fila. */
@Repository
class ReportesJdbcAdapter implements ReportesRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String VENTAS = " FROM cuenta WHERE local_id=:local AND estado='CERRADA' "
            + "AND cerrada_en>=:desde AND cerrada_en<:hasta";
    private static final String TIEMPOS = " FROM pedido WHERE local_id=:local AND listo_en>=:desde AND listo_en<:hasta "
            + "AND en_preparacion_en IS NOT NULL AND listo_en>=en_preparacion_en";
    private static final String PLATOS = " FROM item_pedido i JOIN pedido p ON p.id=i.pedido_id AND p.local_id=i.local_id "
            + "JOIN plato pl ON pl.id=i.plato_id AND pl.local_id=i.local_id "
            + "WHERE p.local_id=:local AND p.enviado_en>=:desde AND p.enviado_en<:hasta "
            + "AND p.estado NOT IN ('CANCELADO','RECHAZADO')";

    ReportesJdbcAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Ventas ventas(Long localId, Instant desde, Instant hasta, int page, int size) {
        var parametros = parametros(localId, desde, hasta, page, size);
        var resumen = jdbc.queryForObject("SELECT COUNT(*) cantidad, COALESCE(SUM(total),0) total" + VENTAS,
                parametros, (r, n) -> new Resumen(r.getLong("cantidad"), r.getBigDecimal("total")));
        var detalle = jdbc.query("SELECT id, cerrada_en, total" + VENTAS
                + " ORDER BY cerrada_en DESC, id DESC LIMIT :limite OFFSET :offset", parametros,
                (r, n) -> new Venta(r.getLong("id"), r.getTimestamp("cerrada_en").toInstant(), r.getBigDecimal("total")));
        return new Ventas(resumen.valor(), resumen.cantidad(), new PaginaDe<>(detalle, resumen.cantidad()));
    }

    @Override
    public Tiempos tiempos(Long localId, Instant desde, Instant hasta, int page, int size) {
        var parametros = parametros(localId, desde, hasta, page, size);
        var resumen = jdbc.queryForObject("SELECT COUNT(*) cantidad, AVG(TIMESTAMPDIFF(SECOND,en_preparacion_en,listo_en)) promedio"
                + TIEMPOS, parametros, (r, n) -> new Resumen(r.getLong("cantidad"), r.getBigDecimal("promedio")));
        var detalle = jdbc.query("SELECT id,en_preparacion_en,listo_en,TIMESTAMPDIFF(SECOND,en_preparacion_en,listo_en) segundos"
                + TIEMPOS + " ORDER BY listo_en DESC,id DESC LIMIT :limite OFFSET :offset", parametros,
                (r, n) -> new Tiempo(r.getLong("id"), r.getTimestamp("en_preparacion_en").toInstant(),
                        r.getTimestamp("listo_en").toInstant(), r.getLong("segundos")));
        long cantidad = resumen.cantidad();
        BigDecimal promedio = resumen.valor() == null ? null : resumen.valor().setScale(2, RoundingMode.HALF_UP);
        return new Tiempos(cantidad, promedio, new PaginaDe<>(detalle, cantidad));
    }

    @Override
    public PaginaDe<PlatoPedido> platos(Long localId, Instant desde, Instant hasta, int page, int size) {
        var parametros = parametros(localId, desde, hasta, page, size);
        long cantidad = jdbc.queryForObject("SELECT COUNT(DISTINCT i.plato_id)" + PLATOS, parametros, Long.class);
        var detalle = jdbc.query("SELECT i.plato_id,pl.nombre,SUM(i.cantidad) cantidad" + PLATOS
                + " GROUP BY i.plato_id,pl.nombre ORDER BY cantidad DESC,i.plato_id ASC LIMIT :limite OFFSET :offset", parametros,
                (r, n) -> new PlatoPedido(r.getLong("plato_id"), r.getString("nombre"), r.getLong("cantidad")));
        return new PaginaDe<>(detalle, cantidad);
    }

    private static Map<String, Object> parametros(Long local, Instant desde, Instant hasta, int page, int size) {
        return Map.of("local", local, "desde", Timestamp.from(desde), "hasta", Timestamp.from(hasta),
                "limite", size, "offset", (long) page * size);
    }

    private record Resumen(long cantidad, BigDecimal valor) { }
}
