package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.chitgo.pedidos.dominio.puertos.PedidosDePantalla;

@Repository
class PedidosDePantallaJdbc implements PedidosDePantalla {
    private final JdbcTemplate jdbc;
    PedidosDePantallaJdbc(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<PedidoVisible> delLocal(Long localId) {
        return jdbc.query("""
                SELECT p.id,m.numero,p.estado FROM pedido p
                JOIN cuenta c ON c.id=p.cuenta_id AND c.local_id=p.local_id
                JOIN mesa m ON m.id=c.mesa_id AND m.local_id=p.local_id
                WHERE p.local_id=? AND p.estado IN ('EN_PREPARACION','LISTO') AND c.estado<>'CERRADA'
                ORDER BY p.enviado_en,p.id
                """, (r, n) -> new PedidoVisible(r.getLong("id"), r.getString("numero"), r.getString("estado")), localId);
    }
}
