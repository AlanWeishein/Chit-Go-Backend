package com.chitgo.compartido.infraestructura;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Con el perfil {@code demo}, no deja que la semilla de la entrega ({@code db/demo/V100}) caiga encima de los datos de
 * otro: si la base ya tiene algún local y la V100 no está aplicada, corta el arranque antes de que Flyway migre nada.
 *
 * <p>Sobre una base vacía (sin la tabla {@code local}, o sin filas) migra normal. Si la V100 ya está aplicada, también:
 * es el segundo arranque de una base de la demo.
 */
@Configuration
@Profile("demo")
class FrenoDeLaSemillaDemo {

    static final String VERSION_DE_LA_SEMILLA = "100";
    static final String MENSAJE = "Esta base ya tiene datos: sacá el perfil demo. La semilla de la demo (V100) "
            + "solo se carga sobre una base vacía.";

    @Bean
    FlywayMigrationStrategy migrarSoloSiLaSemillaNoPisaNada() {
        return flyway -> {
            if (tieneDatosSinLaSemilla(flyway)) {
                throw new IllegalStateException(MENSAJE);
            }
            flyway.migrate();
        };
    }

    private static boolean tieneDatosSinLaSemilla(Flyway flyway) {
        DataSource dataSource = flyway.getConfiguration().getDataSource();
        String historial = flyway.getConfiguration().getTable();
        try (Connection conexion = dataSource.getConnection()) {
            return existeLaTabla(conexion, "local")
                    && tieneFilas(conexion)
                    && !laSemillaEstaAplicada(conexion, historial);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo revisar la base antes de cargar la semilla de la demo", e);
        }
    }

    private static boolean existeLaTabla(Connection conexion, String tabla) throws SQLException {
        DatabaseMetaData metadatos = conexion.getMetaData();
        try (ResultSet tablas = metadatos.getTables(conexion.getCatalog(), null, tabla, new String[] { "TABLE" })) {
            return tablas.next();
        }
    }

    private static boolean tieneFilas(Connection conexion) throws SQLException {
        try (Statement consulta = conexion.createStatement();
                ResultSet fila = consulta.executeQuery("SELECT 1 FROM local LIMIT 1")) {
            return fila.next();
        }
    }

    private static boolean laSemillaEstaAplicada(Connection conexion, String historial) throws SQLException {
        if (!existeLaTabla(conexion, historial)) {
            return false;
        }
        // El nombre de la tabla sale de la configuración de Flyway, no de un dato de afuera.
        String sql = "SELECT 1 FROM " + historial + " WHERE version = ? AND success = 1";
        try (PreparedStatement consulta = conexion.prepareStatement(sql)) {
            consulta.setString(1, VERSION_DE_LA_SEMILLA);
            try (ResultSet fila = consulta.executeQuery()) {
                return fila.next();
            }
        }
    }
}
