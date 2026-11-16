package com.chitgo.usuarios.infraestructura.seguridad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Misma equivalencia que usuario.email, incluso para emails inexistentes. No consulta usuarios.
 * Las claves son efímeras, se recalculan en el MySQL en uso y nunca se persisten ni se registran.
 */
@Component
public class ClaveDeEmailParaLimite {

    private final JdbcTemplate jdbc;

    public ClaveDeEmailParaLimite(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String de(String email) {
        return jdbc.queryForObject("""
                SELECT SHA2(WEIGHT_STRING(CONVERT(? USING utf8mb4) COLLATE utf8mb4_0900_ai_ci), 256)
                """, String.class, email.strip());
    }
}
