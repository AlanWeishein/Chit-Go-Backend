package com.chitgo.usuarios.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.AusenciaDto;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeEmpleado;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAusencia;
import com.chitgo.usuarios.aplicacion.dto.DatosDeEdicionDeEmpleado;
import com.chitgo.usuarios.aplicacion.dto.DatosDeTurno;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoResumenDto;
import com.chitgo.usuarios.aplicacion.dto.PaginaDto;
import com.chitgo.usuarios.aplicacion.dto.TurnoDto;
import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.PaginaDe;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.TipoAusencia;
import com.chitgo.usuarios.dominio.Turno;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.MesasDelLocal;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-14: el Admin da de alta, edita, lista y da de baja a Mozo, Caja y Cocina, y les carga los turnos, las mesas
 * (solo al mozo) y las ausencias (RF-26). El blanqueo del PIN llega en la tarea 3 de la ST-22.
 *
 * <p>Un empleado, una mesa o una ausencia de otro local da 404, el mismo que si no existiera (RNF-10). El Admin del
 * local tampoco es un empleado que se gestione: su id da 404. Un dado de baja queda de solo lectura (409).
 */
@Service
public class GestionarEmpleadosService {

    static final String ENTIDAD = "EMPLEADO";
    static final String ENTIDAD_AUSENCIA = "AUSENCIA";
    static final int TAMANIO_MAXIMO = 100;
    static final String NO_EXISTE = "El empleado no existe";
    static final String DADO_DE_BAJA = "El empleado está dado de baja";
    static final String USUARIO_REPETIDO = "Ya existe un usuario con ese nombre";
    static final String MESA_NO_EXISTE = "La mesa no existe";
    static final String AUSENCIA_NO_EXISTE = "La ausencia no existe";

    /** Las ausencias son días del local, como en MozoDeTurnoAdapter. */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EmpleadoRepository empleados;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final MesasDelLocal mesas;
    private final CodificadorDeClaves codificador;
    private final Auditor auditor;
    private final Reloj reloj;

    public GestionarEmpleadosService(EmpleadoRepository empleados, UsuarioRepository usuarios, RolRepository roles,
            MesasDelLocal mesas, CodificadorDeClaves codificador, Auditor auditor, Reloj reloj) {
        this.empleados = empleados;
        this.usuarios = usuarios;
        this.roles = roles;
        this.mesas = mesas;
        this.codificador = codificador;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    /**
     * P-16: una página de los empleados del local, ordenada por nombre. Los dados de baja también salen. Son
     * cuatro consultas sin importar el tamaño: la página, su total, los turnos y las ausencias vigentes.
     *
     * @param page desde 0
     * @throws SolicitudInvalidaException si page es negativa o size no está entre 1 y 100
     */
    @Transactional(readOnly = true)
    public PaginaDto<EmpleadoResumenDto> listar(UsuarioAutenticado usuario, int page, int size) {
        Long localId = localDe(usuario);
        if (page < 0 || size < 1 || size > TAMANIO_MAXIMO) {
            throw new SolicitudInvalidaException("La página o el tamaño no son válidos");
        }
        PaginaDe<Empleado> pagina = empleados.listarPagina(localId, page, size);
        List<Long> ids = pagina.contenido().stream().map(Empleado::getId).toList();
        Map<Long, List<Turno>> turnos = empleados.turnosDe(ids, localId).stream()
                .collect(Collectors.groupingBy(t -> t.getEmpleado().getId()));
        Set<Long> conAusencia = empleados.conAusenciaVigente(ids, localId, hoy());

        List<EmpleadoResumenDto> filas = pagina.contenido().stream()
                .map(e -> new EmpleadoResumenDto(e.getId(), e.getUsuario().getNombre(),
                        e.getUsuario().getRol().getCodigo(), e.getUsuario().getNombreUsuario(), e.estaActivo(),
                        e.getUsuario().estaBloqueado(reloj.ahora()), conAusencia.contains(e.getId()),
                        aTurnosDto(turnos.getOrDefault(e.getId(), List.of()))))
                .toList();
        int totalPaginas = (int) ((pagina.total() + size - 1) / size);
        return new PaginaDto<>(filas, page, size, pagina.total(), totalPaginas);
    }

    /** P-23: el detalle, con turnos, mesas y ausencias. */
    @Transactional(readOnly = true)
    public EmpleadoDetalleDto detalle(UsuarioAutenticado usuario, Long empleadoId) {
        Long localId = localDe(usuario);
        return aDetalle(buscar(empleadoId, localId), localId);
    }

    /**
     * Crea el usuario y el empleado. Nace con la marca de cambio obligatorio y, si es mozo, con la contraseña
     * provisoria. Solo Mozo, Caja y Cocina.
     *
     * @throws SolicitudInvalidaException si falta un dato o sobra uno que el rol no usa
     * @throws ReglaDeNegocioException    si el usuario del mozo ya existe en el local (409)
     */
    @Transactional
    public EmpleadoDetalleDto crear(UsuarioAutenticado usuario, DatosDeAltaDeEmpleado datos) {
        Long localId = localDe(usuario);
        String nombre = nombreValido(datos.nombre());
        String codigoRol = codigoDeRol(datos.rol());
        boolean esMozo = "MOZO".equals(codigoRol);
        if (datos.pin() == null || datos.pin().isBlank()) {
            throw new SolicitudInvalidaException("Falta el PIN");
        }
        String nombreUsuario = textoOVacio(datos.nombreUsuario());
        if (esMozo) {
            if (nombreUsuario == null) {
                throw new SolicitudInvalidaException("Falta el usuario del mozo");
            }
            if (datos.password() == null) {
                throw new SolicitudInvalidaException("Falta la contraseña del mozo");
            }
            verificarQueNoExiste(localId, nombreUsuario, null);
        } else if (nombreUsuario != null || datos.password() != null) {
            throw new SolicitudInvalidaException("Solo el mozo lleva usuario y contraseña");
        }

        // Los roles son datos fijos de la V2: si falta uno, es un error de la base y no de la solicitud.
        Rol rol = roles.buscarPorCodigo(codigoRol).orElseThrow();
        String hashPassword = esMozo ? codificador.hashear(datos.password()) : null;
        Usuario nuevo = usuarios.guardar(Usuario.nuevoEmpleado(localId, rol, nombre, nombreUsuario, hashPassword,
                codificador.hashear(datos.pin())));
        Empleado empleado = empleados.guardar(new Empleado(localId, nuevo));

        Map<String, Object> valores = valores(empleado);
        valores.put("rol", codigoRol);
        valores.put("activo", true);
        auditor.registrar(localId, usuario.usuarioId(), "CREAR_EMPLEADO", ENTIDAD, empleado.getId(), null, valores);
        return aDetalle(empleado, localId);
    }

    /**
     * Cambia el nombre y, si es mozo, su usuario. El rol, el PIN y la contraseña no cambian. La auditoría lleva
     * solo lo que cambió; si no cambió nada, no audita.
     *
     * @throws ReglaDeNegocioException si está dado de baja o el usuario ya lo tiene otro (409)
     */
    @Transactional
    public EmpleadoDetalleDto editar(UsuarioAutenticado usuario, Long empleadoId, DatosDeEdicionDeEmpleado datos) {
        Long localId = localDe(usuario);
        Empleado empleado = modificable(empleadoId, localId);
        String nombre = nombreValido(datos.nombre());
        String nombreUsuario = textoOVacio(datos.nombreUsuario());
        if (empleado.getUsuario().getRol().esMozo()) {
            if (nombreUsuario == null) {
                throw new SolicitudInvalidaException("Falta el usuario del mozo");
            }
            verificarQueNoExiste(localId, nombreUsuario, empleado.getUsuario().getId());
        } else if (nombreUsuario != null) {
            throw new SolicitudInvalidaException("Solo el mozo lleva usuario");
        }

        Map<String, Object> antes = valores(empleado);
        empleado.renombrar(nombre, nombreUsuario);
        Map<String, Object> despues = valores(empleado);

        antes.keySet().removeIf(clave -> Objects.equals(antes.get(clave), despues.get(clave)));
        if (!antes.isEmpty()) {
            despues.keySet().retainAll(antes.keySet());
            usuarios.guardar(empleado.getUsuario());
            auditor.registrar(localId, usuario.usuarioId(), "EDITAR_EMPLEADO", ENTIDAD, empleado.getId(), antes,
                    despues);
        }
        return aDetalle(empleado, localId);
    }

    /** Baja lógica: no borra filas. Si ya estaba dado de baja, no cambia nada y no audita. */
    @Transactional
    public void darDeBaja(UsuarioAutenticado usuario, Long empleadoId) {
        Long localId = localDe(usuario);
        Empleado empleado = empleados.buscarParaModificar(empleadoId, localId)
                .orElseThrow(() -> new NoEncontradoException(NO_EXISTE));
        if (!empleado.darDeBaja()) {
            return;
        }
        usuarios.guardar(empleado.getUsuario());
        auditor.registrar(localId, usuario.usuarioId(), "BAJA_EMPLEADO", ENTIDAD, empleado.getId(),
                Map.of("activo", true), Map.of("activo", false));
    }

    /**
     * Reemplaza la plantilla semanal entera; la lista vacía lo deja sin turnos. Un turno que termina a una hora menor
     * que la de inicio sigue al día siguiente (P-23c). Si no cambió nada, no guarda ni audita.
     *
     * @throws SolicitudInvalidaException si a un turno le falta un dato, empieza y termina a la misma hora o se pisa
     *                                    con otro
     * @throws ReglaDeNegocioException    si está dado de baja (409)
     */
    @Transactional
    public EmpleadoDetalleDto reemplazarTurnos(UsuarioAutenticado usuario, Long empleadoId, List<DatosDeTurno> datos) {
        Long localId = localDe(usuario);
        Empleado empleado = modificable(empleadoId, localId);
        if (datos == null) {
            throw new SolicitudInvalidaException("Faltan los turnos");
        }
        List<Turno> nuevos = datos.stream().map(d -> turno(localId, empleado, d)).toList();
        Turno.verificarQueNoSePisan(nuevos);

        List<String> antes = descripciones(empleados.turnosDe(List.of(empleado.getId()), localId));
        List<String> despues = descripciones(nuevos);
        if (!antes.equals(despues)) {
            empleados.reemplazarTurnos(empleado.getId(), localId, nuevos);
            auditor.registrar(localId, usuario.usuarioId(), "CAMBIAR_TURNOS", ENTIDAD, empleado.getId(),
                    Map.of("turnos", antes), Map.of("turnos", despues));
        }
        return aDetalle(empleado, localId);
    }

    /**
     * Reemplaza las mesas del mozo; la lista vacía lo deja sin mesas. Una mesa puede tener varios mozos (CP-27-02).
     * Si una no existe o es de otro local, no se guarda ninguna. Si no cambió nada, no audita.
     *
     * @throws SolicitudInvalidaException si no es mozo o falta una mesa
     * @throws ReglaDeNegocioException    si está dado de baja (409)
     * @throws NoEncontradoException      si una mesa no es del local
     */
    @Transactional
    public EmpleadoDetalleDto reemplazarMesas(UsuarioAutenticado usuario, Long empleadoId, List<Long> mesaIds) {
        Long localId = localDe(usuario);
        Empleado empleado = modificable(empleadoId, localId);
        if (!empleado.getUsuario().getRol().esMozo()) {
            throw new SolicitudInvalidaException("Solo el mozo tiene mesas asignadas");
        }
        if (mesaIds == null) {
            throw new SolicitudInvalidaException("Faltan las mesas");
        }
        if (mesaIds.stream().anyMatch(Objects::isNull)) {
            throw new SolicitudInvalidaException("Falta el id de una mesa");
        }
        Set<Long> pedidas = new TreeSet<>(mesaIds);
        if (!pedidas.isEmpty() && !mesas.deEsteLocal(pedidas, localId).containsAll(pedidas)) {
            throw new NoEncontradoException(MESA_NO_EXISTE);
        }

        List<Long> antes = empleado.getMesaIds().stream().sorted().toList();
        // El empleado está gestionado: al cerrar la transacción se borran las que salieron y se insertan las nuevas.
        if (empleado.reemplazarMesas(pedidas)) {
            auditor.registrar(localId, usuario.usuarioId(), "CAMBIAR_MESAS", ENTIDAD, empleado.getId(),
                    Map.of("mesaIds", antes), Map.of("mesaIds", List.copyOf(pedidas)));
        }
        return aDetalle(empleado, localId);
    }

    /** Las ausencias del empleado, de la más nueva a la más vieja. También las de un dado de baja. */
    @Transactional(readOnly = true)
    public List<AusenciaDto> ausencias(UsuarioAutenticado usuario, Long empleadoId) {
        Long localId = localDe(usuario);
        Empleado empleado = buscar(empleadoId, localId);
        return aAusenciasDto(empleados.ausenciasDe(empleado.getId(), localId));
    }

    /**
     * Carga una ausencia, con o sin fecha de vuelta. La firma el Admin de la sesión.
     *
     * @throws SolicitudInvalidaException si falta el tipo o el inicio, o si termina antes de empezar
     * @throws ReglaDeNegocioException    si está dado de baja (409)
     */
    @Transactional
    public AusenciaDto cargarAusencia(UsuarioAutenticado usuario, Long empleadoId, DatosDeAusencia datos) {
        Long localId = localDe(usuario);
        Empleado empleado = modificable(empleadoId, localId);
        Ausencia ausencia = empleados.guardarAusencia(new Ausencia(localId, empleado, tipo(datos.tipo()),
                datos.desde(), datos.hasta(), usuario.usuarioId()));

        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("empleadoId", empleado.getId());
        valores.put("tipo", ausencia.getTipo().name());
        valores.put("desde", ausencia.getDesde().toString());
        valores.put("hasta", texto(ausencia.getHasta()));
        auditor.registrar(localId, usuario.usuarioId(), "CREAR_AUSENCIA", ENTIDAD_AUSENCIA, ausencia.getId(), null,
                valores);
        return aAusenciaDto(ausencia);
    }

    /**
     * Termina la ausencia o cambia su fecha de vuelta. No se borra: así queda de cuándo a cuándo faltó. Si ya
     * terminaba ese día, no guarda ni audita.
     *
     * @throws SolicitudInvalidaException si falta la fecha o es anterior al inicio
     * @throws ReglaDeNegocioException    si el empleado está dado de baja (409)
     * @throws NoEncontradoException      si la ausencia no es de ese empleado o de ese local
     */
    @Transactional
    public AusenciaDto cambiarFinDeAusencia(UsuarioAutenticado usuario, Long empleadoId, Long ausenciaId,
            LocalDate hasta) {
        Long localId = localDe(usuario);
        Empleado empleado = modificable(empleadoId, localId);
        Ausencia ausencia = empleados.buscarAusencia(ausenciaId, empleado.getId(), localId)
                .orElseThrow(() -> new NoEncontradoException(AUSENCIA_NO_EXISTE));
        String antes = texto(ausencia.getHasta());
        if (ausencia.cambiarHasta(hasta)) {
            empleados.guardarAusencia(ausencia);
            auditor.registrar(localId, usuario.usuarioId(), "EDITAR_AUSENCIA", ENTIDAD_AUSENCIA, ausencia.getId(),
                    Collections.singletonMap("hasta", antes), Map.of("hasta", hasta.toString()));
        }
        return aAusenciaDto(ausencia);
    }

    private Empleado buscar(Long empleadoId, Long localId) {
        return empleados.buscarPorId(empleadoId, localId).orElseThrow(() -> new NoEncontradoException(NO_EXISTE));
    }

    /** Para cambiarlo: con la fila bloqueada, y un dado de baja queda de solo lectura. */
    private Empleado modificable(Long empleadoId, Long localId) {
        Empleado empleado = empleados.buscarParaModificar(empleadoId, localId)
                .orElseThrow(() -> new NoEncontradoException(NO_EXISTE));
        if (!empleado.estaActivo()) {
            throw new ReglaDeNegocioException(DADO_DE_BAJA);
        }
        return empleado;
    }

    private static Turno turno(Long localId, Empleado empleado, DatosDeTurno datos) {
        if (datos == null) {
            throw new SolicitudInvalidaException("Falta un turno");
        }
        return new Turno(localId, empleado, dia(datos.dia()), datos.horaInicio(), datos.horaFin());
    }

    /** Llega como texto, así un día inventado da el 400 con mensaje y no el genérico de Jackson. */
    private static DiaSemana dia(String dia) {
        if (dia == null || dia.isBlank()) {
            throw new SolicitudInvalidaException("Falta el día del turno");
        }
        try {
            return DiaSemana.valueOf(dia.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException("El día tiene que ser LUN, MAR, MIE, JUE, VIE, SAB o DOM");
        }
    }

    private static TipoAusencia tipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalidaException("Falta el tipo de ausencia");
        }
        try {
            return TipoAusencia.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException("El tipo tiene que ser VACACIONES, FRANCO o BAJA_TEMPORAL");
        }
    }

    /** Para la auditoría y para ver si cambió algo: "LUN 12:00-16:00", en orden. */
    private static List<String> descripciones(List<Turno> turnos) {
        return turnos.stream().sorted(Turno.EN_ORDEN).map(Turno::describir).toList();
    }

    private static String texto(LocalDate fecha) {
        return fecha == null ? null : fecha.toString();
    }

    private void verificarQueNoExiste(Long localId, String nombreUsuario, Long excluirUsuarioId) {
        if (usuarios.existeNombreDeUsuario(localId, nombreUsuario, excluirUsuarioId)) {
            throw new ReglaDeNegocioException(USUARIO_REPETIDO);
        }
    }

    private static String nombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalidaException("Falta el nombre");
        }
        return nombre.trim();
    }

    private static String codigoDeRol(String rol) {
        if (rol == null || rol.isBlank()) {
            throw new SolicitudInvalidaException("Falta el rol");
        }
        String codigo = rol.trim().toUpperCase(Locale.ROOT);
        if (!Empleado.ROLES_GESTIONABLES.contains(codigo)) {
            throw new SolicitudInvalidaException("El rol tiene que ser Mozo, Caja o Cocina");
        }
        return codigo;
    }

    /** Un texto vacío cuenta como que no vino. */
    private static String textoOVacio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /** Lo que queda en la auditoría: nunca un PIN, una contraseña ni un hash. */
    private static Map<String, Object> valores(Empleado empleado) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", empleado.getUsuario().getNombre());
        valores.put("nombreUsuario", empleado.getUsuario().getNombreUsuario());
        return valores;
    }

    private EmpleadoDetalleDto aDetalle(Empleado empleado, Long localId) {
        Usuario u = empleado.getUsuario();
        List<TurnoDto> turnos = aTurnosDto(empleados.turnosDe(List.of(empleado.getId()), localId));
        List<AusenciaDto> ausencias = aAusenciasDto(empleados.ausenciasDe(empleado.getId(), localId));
        LocalDate hoy = hoy();
        boolean vigente = ausencias.stream()
                .anyMatch(a -> !a.desde().isAfter(hoy) && (a.hasta() == null || !a.hasta().isBefore(hoy)));
        List<Long> mesaIds = empleado.getMesaIds().stream().sorted().toList();
        return new EmpleadoDetalleDto(empleado.getId(), u.getNombre(), u.getRol().getCodigo(), u.getNombreUsuario(),
                u.isActivo(), u.estaBloqueado(reloj.ahora()), vigente, u.isDebeCambiarCredencial(),
                u.isContrasenaProvisoria(), turnos, mesaIds, ausencias);
    }

    /** Por día de la semana y después por hora de inicio. */
    private static List<TurnoDto> aTurnosDto(List<Turno> turnos) {
        return turnos.stream()
                .sorted(Turno.EN_ORDEN)
                .map(t -> new TurnoDto(t.getDiaSemana(), t.getHoraInicio(), t.getHoraFin()))
                .toList();
    }

    private static List<AusenciaDto> aAusenciasDto(List<Ausencia> ausencias) {
        return ausencias.stream().map(GestionarEmpleadosService::aAusenciaDto).toList();
    }

    private static AusenciaDto aAusenciaDto(Ausencia a) {
        return new AusenciaDto(a.getId(), a.getTipo(), a.getDesde(), a.getHasta());
    }

    private LocalDate hoy() {
        return reloj.ahora().atZone(ZONA).toLocalDate();
    }

    /** El Gerente/Dueño no tiene local fijo y no gestiona empleados. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
