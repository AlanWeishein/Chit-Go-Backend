package com.chitgo.usuarios;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Cuenta las consultas que Hibernate manda a la base, para MedicionDeConsultasTest. Se registra con la propiedad
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}. Solo ve a Hibernate: lo que se arma
 * con JdbcTemplate no entra.
 */
public class ContadorDeSql implements StatementInspector {

    private static final List<String> CONSULTAS = new CopyOnWriteArrayList<>();

    public static void reiniciar() {
        CONSULTAS.clear();
    }

    public static List<String> consultas() {
        return List.copyOf(CONSULTAS);
    }

    @Override
    public String inspect(String sql) {
        CONSULTAS.add(sql);
        return sql;
    }
}
