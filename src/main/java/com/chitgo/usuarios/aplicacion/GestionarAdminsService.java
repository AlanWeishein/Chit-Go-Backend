package com.chitgo.usuarios.aplicacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.AdminDto;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeAdmin;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-16 (RF-29, RNF-07): el Gerente/Dueño lista, crea, da de baja, reactiva y desbloquea a los Admin de su local.
 *
 * <p>El local es el que lo tiene como propietario, nunca uno que mande el pedido. Un id que no es un Admin de ese
 * local (otro rol, otro local o que no existe) da 404 "El Admin no existe", sin nombrar a ningún local (RNF-10).
 * Un dado de baja queda de solo lectura: desbloquearlo da 409, como el blanqueo de HU-14.
 */
@Service
public class GestionarAdminsService {

    static final String ENTIDAD = "USUARIO";
    static final String ROL_ADMIN = "ADMIN";
    static final String NO_EXISTE = "El Admin no existe";
    static final String LOCAL_NO_EXISTE = "El local no existe";
    static final String DADO_DE_BAJA = "El Admin está dado de baja";
    static final String EMAIL_REPETIDO = "Ya hay un usuario con ese email";

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;
    private final EmpleadoRepository empleados;
    private final RolRepository roles;
    private final CodificadorDeClaves codificador;
    private final Auditor auditor;
    private final Reloj reloj;

    public GestionarAdminsService(LocalRepository locales, UsuarioRepository usuarios, EmpleadoRepository empleados,
            RolRepository roles, CodificadorDeClaves codificador, Auditor auditor, Reloj reloj) {
        this.locales = locales;
        this.usuarios = usuarios;
        this.empleados = empleados;
        this.roles = roles;
        this.codificador = codificador;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    /** P-19: los Admin del local por nombre, también los dados de baja. Son pocos por local: sin paginado. */
    @Transactional(readOnly = true)
    public List<AdminDto> listar(UsuarioAutenticado gerente) {
        Long localId = localDe(gerente);
        return usuarios.listarAdminsDelLocal(localId).stream().map(this::aDto).toList();
    }

    /**
     * Crea el Admin con la contraseña provisoria y la marca de cambio obligatorio (CP-31-01). Puede haber varios
     * (CP-31-02). Lleva su fila de empleado, como el Admin de la seed.
     *
     * @throws SolicitudInvalidaException si falta un dato
     * @throws ReglaDeNegocioException    si el email ya lo tiene otro usuario, de cualquier local (409, CP-31-03)
     */
    @Transactional
    public AdminDto crear(UsuarioAutenticado gerente, DatosDeAltaDeAdmin datos) {
        Long localId = localDe(gerente);
        String nombre = requerido(datos.nombre(), "Falta el nombre");
        String email = requerido(datos.email(), "Falta el email");
        if (datos.password() == null || datos.password().isEmpty()) {
            throw new SolicitudInvalidaException("Falta la contraseña");
        }
        if (usuarios.existeEmail(email)) {
            throw new ReglaDeNegocioException(EMAIL_REPETIDO);
        }

        // Los roles son datos fijos de la V2: si falta uno, es un error de la base y no de la solicitud.
        Rol rol = roles.buscarPorCodigo(ROL_ADMIN).orElseThrow();
        Usuario admin = usuarios.guardarConEmail(
                Usuario.nuevoAdmin(localId, rol, nombre, email, codificador.hashear(datos.password())));
        empleados.guardar(new Empleado(localId, admin));

        auditor.registrar(localId, gerente.usuarioId(), "CREAR_ADMIN", ENTIDAD, admin.getId(), null, valores(admin));
        return aDto(admin);
    }

    /**
     * Baja o reactivación (CP-31-04). La baja no borra nada: el historial del Admin queda. Mandar el estado que ya
     * tiene no guarda ni audita.
     *
     * @throws SolicitudInvalidaException si falta activo
     */
    @Transactional
    public AdminDto cambiarActivo(UsuarioAutenticado gerente, Long adminId, Boolean activo) {
        Long localId = localDe(gerente);
        Usuario admin = buscar(adminId, localId);
        if (activo == null) {
            throw new SolicitudInvalidaException("Falta activo");
        }
        boolean cambio = activo ? admin.reactivar() : admin.darDeBaja();
        if (cambio) {
            usuarios.guardar(admin);
            auditor.registrar(localId, gerente.usuarioId(), activo ? "REACTIVAR_ADMIN" : "BAJA_ADMIN", ENTIDAD,
                    admin.getId(), Map.of("activo", !activo), Map.of("activo", activo));
        }
        return aDto(admin);
    }

    /**
     * Levanta el bloqueo por intentos fallidos: los intentos vuelven a 0. Si no estaba bloqueado, no cambia nada y no
     * audita.
     *
     * @throws ReglaDeNegocioException si está dado de baja (409)
     */
    @Transactional
    public AdminDto desbloquear(UsuarioAutenticado gerente, Long adminId) {
        Long localId = localDe(gerente);
        Usuario admin = buscar(adminId, localId);
        if (!admin.isActivo()) {
            throw new ReglaDeNegocioException(DADO_DE_BAJA);
        }
        Map<String, Object> antes = estadoDelBloqueo(admin);
        if (admin.desbloquear(reloj.ahora())) {
            usuarios.guardar(admin);
            auditor.registrar(localId, gerente.usuarioId(), "DESBLOQUEAR_ADMIN", ENTIDAD, admin.getId(), antes,
                    estadoDelBloqueo(admin));
        }
        return aDto(admin);
    }

    // --- Lo que comparten

    /** El local del que es propietario. Un Admin, Caja o Mozo no es propietario de ninguno. */
    private Long localDe(UsuarioAutenticado gerente) {
        return locales.buscarPorPropietario(gerente.usuarioId()).map(Local::getId)
                .orElseThrow(() -> new NoEncontradoException(LOCAL_NO_EXISTE));
    }

    private Usuario buscar(Long adminId, Long localId) {
        return usuarios.buscarAdminParaModificar(adminId, localId)
                .orElseThrow(() -> new NoEncontradoException(NO_EXISTE));
    }

    private static String requerido(String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            throw new SolicitudInvalidaException(mensaje);
        }
        return texto.strip();
    }

    /** Lo que queda en la auditoría del alta: nunca la contraseña ni su hash. */
    private static Map<String, Object> valores(Usuario admin) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", admin.getNombre());
        valores.put("email", admin.getEmail());
        valores.put("activo", admin.isActivo());
        return valores;
    }

    private Map<String, Object> estadoDelBloqueo(Usuario admin) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("intentosFallidos", (int) admin.getIntentosFallidos());
        valores.put("bloqueado", admin.estaBloqueado(reloj.ahora()));
        return valores;
    }

    private AdminDto aDto(Usuario admin) {
        return new AdminDto(admin.getId(), admin.getNombre(), admin.getEmail(), admin.isActivo(),
                admin.estaBloqueado(reloj.ahora()));
    }
}
