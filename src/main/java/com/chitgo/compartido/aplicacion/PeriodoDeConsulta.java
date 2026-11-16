package com.chitgo.compartido.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** Fechas inclusivas de la zona del local, traducidas a [desde, hastaExclusivo) en UTC. */
public record PeriodoDeConsulta(Instant desde, Instant hastaExclusivo) {
    public static PeriodoDeConsulta de(LocalDate desde, LocalDate hasta, ZoneId zona, boolean obligatorio) {
        if ((obligatorio && (desde == null || hasta == null))
                || (desde != null && hasta != null && desde.isAfter(hasta))
                || (desde != null && (desde.getYear() < 1970 || desde.getYear() > 9998))
                || (hasta != null && (hasta.getYear() < 1970 || hasta.getYear() > 9998))) {
            throw new SolicitudInvalidaException("Elegí un período válido, con desde anterior o igual a hasta");
        }
        return new PeriodoDeConsulta(desde == null ? null : desde.atStartOfDay(zona).toInstant(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(zona).toInstant());
    }
}
