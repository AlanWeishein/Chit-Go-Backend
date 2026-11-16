package com.chitgo.usuarios.infraestructura.script;

import java.io.Console;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;

/**
 * Los comandos del script del local (HU-16): {@code alta-local} y {@code desbloquear-gerente}. Es una cáscara fina
 * sobre {@link AltaDeLocalService}: lee los argumentos, pide la contraseña, muestra el resultado y termina el back.
 *
 * <p>Solo existe con el perfil {@code script}, que no prende nadie más: ni {@code dev} ni los tests. Cómo se corre
 * está en el README, en "Alta de un local".
 *
 * <p>La contraseña nunca va como argumento, porque quedaría en el historial de la terminal y en la lista de procesos:
 * sale de la variable {@value #VARIABLE} o, si no está, se pide por la consola sin mostrarla.
 *
 * <p>Sale con 0 si salió bien (también si el Gerente/Dueño no estaba bloqueado), con 1 si los datos no sirven y no
 * cambió nada, y con 2 si el comando está mal escrito.
 */
@Component
@Profile("script")
class ComandosDelLocal implements ApplicationRunner {

    static final String VARIABLE = "CHITGO_CONTRASENA";
    static final int BIEN = 0;
    static final int NO_CAMBIO_NADA = 1;
    static final int MAL_ESCRITO = 2;

    private static final List<String> OPCIONES_DE_CONTRASENA = List.of("password", "contrasena", "contraseña");

    static final String AYUDA = """
            Comandos del script del local (perfil script):
              alta-local --local=<nombre> [--direccion=<dirección>] --gerente=<nombre> --email=<email>
              desbloquear-gerente --email=<email>
            La contraseña provisoria del Gerente/Dueño no va como argumento: se lee de la variable %s
            o, si no está, se pide por la consola.""".formatted(VARIABLE);

    /** De dónde sale la contraseña. Devuelve NULL si no hay forma de pedirla. */
    @FunctionalInterface
    interface FuenteDeContrasena {
        String leer();
    }

    private final AltaDeLocalService servicio;
    private final ConfigurableApplicationContext contexto;
    private final String urlFront;
    private final FuenteDeContrasena contrasena;
    private final PrintStream salida;

    @Autowired
    ComandosDelLocal(AltaDeLocalService servicio, ConfigurableApplicationContext contexto,
            @Value("${chitgo.script.url-front:http://localhost:5173}") String urlFront) {
        this(servicio, contexto, urlFront, ComandosDelLocal::contrasenaDelEntornoOLaConsola, System.out);
    }

    ComandosDelLocal(AltaDeLocalService servicio, ConfigurableApplicationContext contexto, String urlFront,
            FuenteDeContrasena contrasena, PrintStream salida) {
        this.servicio = servicio;
        this.contexto = contexto;
        this.urlFront = urlFront.endsWith("/") ? urlFront.substring(0, urlFront.length() - 1) : urlFront;
        this.contrasena = contrasena;
        this.salida = salida;
    }

    /** Corre el comando y apaga el back: el script no queda levantado. */
    @Override
    public void run(ApplicationArguments argumentos) {
        int codigo = ejecutar(argumentos);
        System.exit(SpringApplication.exit(contexto, () -> codigo));
    }

    int ejecutar(ApplicationArguments argumentos) {
        if (OPCIONES_DE_CONTRASENA.stream().anyMatch(argumentos::containsOption)) {
            salida.println("La contraseña no va como argumento: queda en el historial de la terminal. "
                    + "Usá la variable " + VARIABLE + " o dejá que te la pida.");
            return MAL_ESCRITO;
        }
        List<String> comandos = argumentos.getNonOptionArgs();
        if (comandos.size() != 1) {
            salida.println(AYUDA);
            return MAL_ESCRITO;
        }
        try {
            return switch (comandos.getFirst()) {
                case "alta-local" -> altaDeLocal(argumentos);
                case "desbloquear-gerente" -> desbloquearGerente(argumentos);
                default -> {
                    salida.println("No existe el comando " + comandos.getFirst() + ".");
                    salida.println(AYUDA);
                    yield MAL_ESCRITO;
                }
            };
        } catch (SolicitudInvalidaException | ReglaDeNegocioException e) {
            salida.println("No se guardó nada: " + e.getMessage() + ".");
            return NO_CAMBIO_NADA;
        }
    }

    private int altaDeLocal(ApplicationArguments argumentos) {
        String clave = contrasena.leer();
        if (clave == null) {
            salida.println("Falta la contraseña: cargala en la variable " + VARIABLE + " o corré el comando "
                    + "desde una terminal para que te la pida.");
            return MAL_ESCRITO;
        }
        LocalCreado local = servicio.darDeAlta(new DatosDeAltaDeLocal(opcion(argumentos, "local"),
                opcion(argumentos, "direccion"), opcion(argumentos, "gerente"), opcion(argumentos, "email"), clave));
        salida.println("Local creado.");
        salida.println("  Local: " + local.nombreLocal() + " (id " + local.localId() + ")");
        salida.println("  Token de staff: " + local.tokenStaff());
        salida.println("  Login del personal: " + urlFront + "/staff/" + local.tokenStaff());
        salida.println("  Gerente/Dueño: " + local.email() + " (id " + local.gerenteId() + "). Entra con su email "
                + "y la contraseña provisoria, y la cambia al entrar.");
        return BIEN;
    }

    private int desbloquearGerente(ApplicationArguments argumentos) {
        String email = opcion(argumentos, "email");
        return switch (servicio.desbloquearGerente(email)) {
            case DESBLOQUEADO -> {
                salida.println("Gerente/Dueño desbloqueado: " + email.strip() + ". Los intentos volvieron a 0.");
                yield BIEN;
            }
            case NO_ESTABA_BLOQUEADO -> {
                salida.println("El Gerente/Dueño " + email.strip() + " no estaba bloqueado. No se cambió nada.");
                yield BIEN;
            }
            case NO_ES_GERENTE -> {
                salida.println("No hay un Gerente/Dueño con el email " + email.strip() + ". No se cambió nada.");
                yield NO_CAMBIO_NADA;
            }
            case SIN_LOCAL -> {
                salida.println("El Gerente/Dueño " + email.strip() + " no tiene un local. No se cambió nada.");
                yield NO_CAMBIO_NADA;
            }
        };
    }

    private static String opcion(ApplicationArguments argumentos, String nombre) {
        List<String> valores = argumentos.getOptionValues(nombre);
        return valores == null || valores.isEmpty() ? null : valores.getFirst();
    }

    /** Primero la variable de entorno; si no está, la consola, dos veces y sin mostrarla. */
    static String contrasenaDelEntornoOLaConsola() {
        String variable = System.getenv(VARIABLE);
        if (variable != null && !variable.isEmpty()) {
            return variable;
        }
        Console consola = System.console();
        if (consola == null) {
            return null;
        }
        char[] una = consola.readPassword("Contraseña provisoria del Gerente/Dueño: ");
        char[] otra = consola.readPassword("Repetila: ");
        try {
            if (una == null || otra == null) {
                return null;
            }
            if (!Arrays.equals(una, otra)) {
                throw new SolicitudInvalidaException("Las dos contraseñas no coinciden");
            }
            return new String(una);
        } finally {
            if (una != null) {
                Arrays.fill(una, ' ');
            }
            if (otra != null) {
                Arrays.fill(otra, ' ');
            }
        }
    }
}
