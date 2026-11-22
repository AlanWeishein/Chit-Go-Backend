package com.chitgo.usuarios.infraestructura.arranque;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

/**
 * El primer local y su Gerente/Dueño, desde el .env, al arrancar el back (ST-32): así una instalación nueva se arma
 * con llenar el .env y {@code docker compose up}, sin correr el script.
 *
 * <ul>
 *   <li>Ninguna variable cargada: no hace nada. Es el caso de dev, de los tests y de la demo.</li>
 *   <li>Algunas sí y otras no: el back no arranca, y el mensaje nombra las que faltan.</li>
 *   <li>Las cinco y la base con algún local: no crea nada y lo deja en el log. Reiniciar no duplica el local, y la
 *       contraseña del .env no pisa la que eligió el Gerente/Dueño.</li>
 *   <li>Las cinco y la base sin locales: crea el local con {@link AltaDeLocalService#darDeAltaDesdeElEntorno}, con la
 *       contraseña como provisoria. Si un dato no sirve, el back no arranca, con el mensaje del alta.</li>
 * </ul>
 *
 * <p>Corre después de Flyway: los runners se llaman con el contexto ya armado, y Flyway migra mientras se arma. No se
 * prende con el perfil {@code script}, que tiene su propio runner. La contraseña nunca va al log.
 *
 * <p>Límite: mira si hay locales y después crea, sin bloquear la tabla. Dos backs arrancando a la vez contra la misma
 * base vacía crearían dos locales. Con un solo contenedor no pasa; si algún día hay varios, hay que revisarlo.
 */
@Component
@Profile("!script")
class PrimerLocalDelEntorno implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PrimerLocalDelEntorno.class);

    /**
     * Lo que viene del .env. Vacío o con solo espacios cuenta como no cargado. El {@code toString} no muestra la
     * contraseña, por si alguien lo manda al log.
     */
    record Variables(String nombreLocal, String direccion, String nombreGerente, String email, String contrasena) {

        /** Las variables sin cargar, con su nombre del .env y en el orden de la tabla. */
        List<String> sinCargar() {
            Map<String, String> porNombre = new LinkedHashMap<>();
            porNombre.put("CHITGO_LOCAL_NOMBRE", nombreLocal);
            porNombre.put("CHITGO_LOCAL_DIRECCION", direccion);
            porNombre.put("CHITGO_GERENTE_NOMBRE", nombreGerente);
            porNombre.put("CHITGO_GERENTE_EMAIL", email);
            porNombre.put("CHITGO_GERENTE_CONTRASENA", contrasena);
            return porNombre.entrySet().stream()
                    .filter(variable -> variable.getValue() == null || variable.getValue().isBlank())
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public String toString() {
            return "Variables[nombreLocal=" + nombreLocal + ", direccion=" + direccion + ", nombreGerente="
                    + nombreGerente + ", email=" + email + ", contrasena=***]";
        }
    }

    static final int CANTIDAD = 5;

    private final AltaDeLocalService servicio;
    private final LocalRepository locales;
    private final Variables variables;
    private final String urlFront;
    private final Consumer<String> log;

    @Autowired
    PrimerLocalDelEntorno(AltaDeLocalService servicio, LocalRepository locales,
            @Value("${chitgo.primer-local.nombre:}") String nombreLocal,
            @Value("${chitgo.primer-local.direccion:}") String direccion,
            @Value("${chitgo.primer-local.gerente-nombre:}") String nombreGerente,
            @Value("${chitgo.primer-local.gerente-email:}") String email,
            @Value("${chitgo.primer-local.gerente-contrasena:}") String contrasena,
            @Value("${chitgo.url-front:http://localhost:5173}") String urlFront) {
        this(servicio, locales, new Variables(nombreLocal, direccion, nombreGerente, email, contrasena), urlFront,
                LOG::info);
    }

    PrimerLocalDelEntorno(AltaDeLocalService servicio, LocalRepository locales, Variables variables, String urlFront,
            Consumer<String> log) {
        this.servicio = servicio;
        this.locales = locales;
        this.variables = variables;
        this.urlFront = urlFront.endsWith("/") ? urlFront.substring(0, urlFront.length() - 1) : urlFront;
        this.log = log;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        crearSiHaceFalta();
    }

    /** @throws IllegalStateException si faltan variables o un dato no sirve: el back no arranca */
    void crearSiHaceFalta() {
        List<String> sinCargar = variables.sinCargar();
        if (sinCargar.size() == CANTIDAD) {
            return;
        }
        if (!sinCargar.isEmpty()) {
            throw new IllegalStateException("Faltan variables del primer local: " + String.join(", ", sinCargar)
                    + ". Van las cinco o ninguna (ver \"Alta de un local\" en el README).");
        }
        if (locales.hayLocales()) {
            log.accept("Primer local: la base ya tiene locales, así que no se creó nada con las variables CHITGO_* "
                    + "del .env.");
            return;
        }
        LocalCreado local;
        try {
            local = servicio.darDeAltaDesdeElEntorno(new DatosDeAltaDeLocal(variables.nombreLocal(),
                    variables.direccion(), variables.nombreGerente(), variables.email(), variables.contrasena()));
        } catch (SolicitudInvalidaException | ReglaDeNegocioException e) {
            throw new IllegalStateException("No se creó el primer local: " + e.getMessage()
                    + ". Revisá las variables CHITGO_* del .env.", e);
        }
        log.accept("Primer local creado: " + local.nombreLocal() + " (id " + local.localId() + ").");
        log.accept("  Login del personal: " + urlFront + "/staff/" + local.tokenStaff());
        log.accept("  Gerente/Dueño: " + local.email() + " (id " + local.gerenteId() + "). Entra con la contraseña "
                + "de CHITGO_GERENTE_CONTRASENA, que es provisoria: la cambia al entrar.");
    }
}
