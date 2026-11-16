package com.chitgo.auditoria.infraestructura.programacion;

import java.sql.Timestamp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.compartido.dominio.Reloj;

/** RNF-13. Mantenimiento interno, sin endpoint; cada lote tiene su propio commit. */
@Component
@ConditionalOnProperty(name="chitgo.auditoria.retencion-habilitada", havingValue="true", matchIfMissing=true)
public class RetencionDeAuditoria {
    private final JdbcTemplate jdbc;
    private final Reloj reloj;
    private final ReglasDelNegocio reglas;
    public RetencionDeAuditoria(JdbcTemplate jdbc, Reloj reloj, ReglasDelNegocio reglas) {
        this.jdbc = jdbc; this.reloj = reloj; this.reglas = reglas;
    }

    @Scheduled(cron="${chitgo.auditoria.retencion-cron:0 0 3 * * *}", zone="${chitgo.reglas.zona-horaria:America/Argentina/Buenos_Aires}")
    public void eliminarVencidos() {
        Timestamp corte = Timestamp.from(reloj.ahora().atZone(reglas.zonaHoraria()).minusMonths(6).toInstant());
        while (jdbc.update("DELETE FROM registro_auditoria WHERE creado_en < ? ORDER BY creado_en,id LIMIT 1000", corte) == 1000) {
            // El índice de fecha y los lotes evitan bloquear todo el historial en una transacción larga.
        }
    }
}
