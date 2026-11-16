package com.chitgo.compartido.aplicacion;

import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Los números del negocio que estaban escritos en el código (punto 14 del 10/11), leídos de {@code chitgo.reglas}
 * en el application.yml. Si falta alguno, o falta toda la sección, vale el de hoy. Un valor que no tiene sentido
 * (cero, negativo, una zona que no existe) corta el arranque, así un error de tipeo no queda andando. Un número sin
 * unidad se lee en la unidad de cada {@code @DurationUnit}, pero en el yml van siempre con la unidad.
 *
 * <p>Va en {@code aplicacion} y no en {@code infraestructura} porque la leen los services de los logins y de la
 * demora, y ningún {@code aplicacion} importa de {@code infraestructura}. El dominio no la conoce: recibe el número.
 *
 * @param demoraDelPedido RF-16: más de esto sin resolverse, el pedido está demorado. En segundos enteros.
 * @param sesionDeCaja la sesión de Caja se corta a este tiempo (09/10). No la configura el Admin.
 * @param sesionSinLocal la del Gerente/Dueño, que no tiene local ni el T que configura el Admin
 * @param vigenciaDelDispositivo RF-13: cada cuánto el mozo vuelve a entrar con usuario y contraseña
 * @param intentosParaBloquear RNF-07: intentos fallidos seguidos para quedar bloqueado
 * @param zonaHoraria la del local: los turnos y las ausencias son horas y días de pared, no UTC
 */
@ConfigurationProperties("chitgo.reglas")
public record ReglasDelNegocio(
        @DurationUnit(ChronoUnit.SECONDS) @DefaultValue("180s") Duration demoraDelPedido,
        @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("1h") Duration sesionDeCaja,
        @DurationUnit(ChronoUnit.MINUTES) @DefaultValue("10m") Duration sesionSinLocal,
        @DurationUnit(ChronoUnit.DAYS) @DefaultValue("7d") Duration vigenciaDelDispositivo,
        @DefaultValue("5") int intentosParaBloquear,
        @DefaultValue(ReglasDelNegocio.ZONA_POR_DEFECTO) ZoneId zonaHoraria) {

    /** Está aparte porque el {@code zone} del {@code cron} de AlertaDeMesaSinMozoProgramada la necesita constante. */
    public static final String ZONA_POR_DEFECTO = "America/Argentina/Buenos_Aires";

    public ReglasDelNegocio {
        positiva("demora-del-pedido", demoraDelPedido);
        positiva("sesion-de-caja", sesionDeCaja);
        positiva("sesion-sin-local", sesionSinLocal);
        positiva("vigencia-del-dispositivo", vigenciaDelDispositivo);
        if (demoraDelPedido.getNano() != 0) {
            // Demora cuenta segundos enteros (a los 180 todavía no, a los 181 sí): con fracción no hay borde claro.
            throw new IllegalArgumentException("chitgo.reglas.demora-del-pedido va en segundos enteros");
        }
        if (intentosParaBloquear < 1) {
            throw new IllegalArgumentException("chitgo.reglas.intentos-para-bloquear tiene que ser 1 o más");
        }
        if (zonaHoraria == null) {
            throw new IllegalArgumentException("Falta chitgo.reglas.zona-horaria");
        }
    }

    private static void positiva(String nombre, Duration valor) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException("chitgo.reglas." + nombre + " tiene que ser mayor que cero");
        }
    }
}
