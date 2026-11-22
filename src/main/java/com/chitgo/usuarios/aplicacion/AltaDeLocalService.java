package com.chitgo.usuarios.aplicacion;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Alta del local y desbloqueo del Gerente/Dueño: conserva las entradas del script de HU-16 y agrega
 * entradas con permisos para el SuperAdmin experimental. El script usa {@code ComandosDelLocal}.
 *
 * <p>Sin un usuario logueado, en la auditoría figura el mismo Gerente/Dueño, con {@code "origen": "script"} en el
 * valor nuevo ({@code "entorno"} si el local sale del .env al arrancar, ST-32): {@code registro_auditoria.usuario_id} es NOT NULL y no hay un usuario del sistema. La auditoría no
 * dice qué persona corrió el script. El prototipo de SuperAdmin usa métodos separados con autor autenticado.
 */
@Service
public class AltaDeLocalService {

    static final String ROL_GERENTE = "GERENTE_DUENO";
    static final String EMAIL_REPETIDO = "Ya hay un usuario con ese email";

    /** Lo que pasó con el desbloqueo. Solo DESBLOQUEADO cambia algo y queda en la auditoría. */
    public enum ResultadoDelDesbloqueo {
        DESBLOQUEADO, NO_ESTABA_BLOQUEADO, NO_ES_GERENTE, SIN_LOCAL
    }

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final CodificadorDeClaves codificador;
    private final Auditor auditor;
    private final Reloj reloj;
    private final Validator validador;

    public AltaDeLocalService(LocalRepository locales, UsuarioRepository usuarios, RolRepository roles,
            CodificadorDeClaves codificador, Auditor auditor, Reloj reloj, Validator validador) {
        this.locales = locales;
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.auditor = auditor;
        this.reloj = reloj;
        this.validador = validador;
    }

    /**
     * En una sola transacción crea al Gerente/Dueño sin local y después el local que lo apunta, con un token de staff
     * nuevo: las dos tablas se apuntan entre sí, como en la semilla de dev. Si algo falla, no queda nada.
     *
     * @throws SolicitudInvalidaException si falta un dato o no tiene el formato, con todos los errores juntos
     * @throws ReglaDeNegocioException    si el email ya lo tiene otro usuario, sin importar las mayúsculas
     */
    @Transactional
    public LocalCreado darDeAlta(DatosDeAltaDeLocal datos) {
        return crear(datos, null, "script");
    }

    /**
     * El mismo alta, para el primer local que se arma desde el .env al arrancar el back (ST-32). Solo cambia el
     * {@code "origen"} de la auditoría, que queda en {@code "entorno"}.
     */
    @Transactional
    public LocalCreado darDeAltaDesdeElEntorno(DatosDeAltaDeLocal datos) {
        return crear(datos, null, "entorno");
    }

    @Transactional
    @PreAuthorize("hasAuthority('local.crear')")
    public LocalCreado darDeAltaComoSuperadmin(UsuarioAutenticado operador, DatosDeAltaDeLocal datos) {
        return crear(datos, operador.usuarioId(), "superadmin");
    }

    private LocalCreado crear(DatosDeAltaDeLocal datos, Long operadorId, String origen) {
        validar(datos);
        if (usuarios.existeEmail(datos.email())) {
            throw new ReglaDeNegocioException(EMAIL_REPETIDO);
        }

        // Los roles son datos fijos de la V2: si falta uno, es un error de la base y no de los datos.
        Rol rol = roles.buscarPorCodigo(ROL_GERENTE).orElseThrow();
        Usuario gerente = usuarios.guardarConEmail(Usuario.nuevoGerente(rol, datos.nombreGerente(), datos.email(),
                codificador.hashear(datos.password())));
        Local local = locales.guardar(new Local(gerente.getId(), datos.nombreLocal(), datos.direccion(),
                UUID.randomUUID().toString()));

        auditor.registrar(local.getId(), operadorId == null ? gerente.getId() : operadorId,
                "CREAR_LOCAL", "LOCAL", local.getId(), null, valores(local, gerente, origen));
        return new LocalCreado(local.getId(), local.getNombre(), local.getTokenStaff(), gerente.getId(),
                gerente.getEmail());
    }

    /**
     * Los intentos vuelven a 0 y se levanta el bloqueo (RNF-07). Si el email no es de un Gerente/Dueño, no tiene un
     * local o no estaba bloqueado, no cambia nada ni audita, y el resultado lo dice.
     *
     * @throws SolicitudInvalidaException si falta el email
     */
    @Transactional
    public ResultadoDelDesbloqueo desbloquearGerente(String email) {
        return desbloquear(email, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('gerente.desbloquear')")
    public ResultadoDelDesbloqueo desbloquearGerenteComoSuperadmin(UsuarioAutenticado operador, String email) {
        return desbloquear(email, operador.usuarioId());
    }

    private ResultadoDelDesbloqueo desbloquear(String email, Long operadorId) {
        if (email == null || email.isBlank()) {
            throw new SolicitudInvalidaException("Falta el email");
        }
        Usuario gerente = usuarios.buscarPorEmailParaIngreso(email.strip())
                .filter(u -> ROL_GERENTE.equals(u.getRol().getCodigo()))
                .orElse(null);
        if (gerente == null) {
            return ResultadoDelDesbloqueo.NO_ES_GERENTE;
        }
        if (operadorId != null && !gerente.isActivo()) {
            throw new ReglaDeNegocioException("El Gerente/Dueño está dado de baja");
        }
        Local local = locales.buscarPorPropietario(gerente.getId()).orElse(null);
        if (local == null) {
            return ResultadoDelDesbloqueo.SIN_LOCAL;
        }
        Map<String, Object> antes = estadoDelBloqueo(gerente);
        if (!gerente.desbloquear(reloj.ahora())) {
            return ResultadoDelDesbloqueo.NO_ESTABA_BLOQUEADO;
        }
        usuarios.guardar(gerente);
        Map<String, Object> despues = estadoDelBloqueo(gerente);
        despues.put("origen", operadorId == null ? "script" : "superadmin");
        auditor.registrar(local.getId(), operadorId == null ? gerente.getId() : operadorId,
                "DESBLOQUEAR_GERENTE", "USUARIO", gerente.getId(), antes, despues);
        return ResultadoDelDesbloqueo.DESBLOQUEADO;
    }

    private void validar(DatosDeAltaDeLocal datos) {
        String errores = validador.validate(datos).stream()
                .sorted(Comparator.comparingInt(AltaDeLocalService::posicionDelCampo))
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining(". "));
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException(errores);
        }
    }

    private static int posicionDelCampo(ConstraintViolation<DatosDeAltaDeLocal> error) {
        return DatosDeAltaDeLocal.CAMPOS.indexOf(error.getPropertyPath().toString());
    }

    /** Lo que queda en la auditoría del alta: nunca la contraseña ni su hash. */
    private static Map<String, Object> valores(Local local, Usuario gerente, String origen) {
        Map<String, Object> delGerente = new LinkedHashMap<>();
        delGerente.put("id", gerente.getId());
        delGerente.put("nombre", gerente.getNombre());
        delGerente.put("email", gerente.getEmail());
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", local.getNombre());
        valores.put("direccion", local.getDireccion());
        valores.put("gerente", delGerente);
        valores.put("origen", origen);
        return valores;
    }

    private Map<String, Object> estadoDelBloqueo(Usuario gerente) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("intentosFallidos", (int) gerente.getIntentosFallidos());
        valores.put("bloqueado", gerente.estaBloqueado(reloj.ahora()));
        return valores;
    }
}
