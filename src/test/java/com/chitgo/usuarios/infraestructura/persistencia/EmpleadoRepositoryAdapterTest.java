package com.chitgo.usuarios.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.PaginaDe;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.TipoAusencia;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * El listado de empleados y sus consultas de HU-14 (tarea 1 de la ST-22) contra el MySQL del Compose y la seed:
 * La Esquina tiene a Ana, Carla, Hugo y Juan (y el Admin, que no se gestiona), y El Puerto a su Juan. Cada test
 * corre en una transacción que se deshace al terminar, así la base queda como la seed.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EmpleadoRepositoryAdapterTest {

    @Autowired
    private EmpleadoRepository empleados;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private RolRepository roles;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    private Long local(String nombre) {
        return jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, nombre);
    }

    private Long esquina() {
        return local("La Esquina");
    }

    private Long puerto() {
        return local("El Puerto");
    }

    private Long empleadoId(String nombre, Long localId) {
        return jdbc.queryForObject("SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id "
                + "WHERE u.nombre = ? AND e.local_id = ?", Long.class, nombre, localId);
    }

    private static List<String> nombres(PaginaDe<Empleado> pagina) {
        return pagina.contenido().stream().map(e -> e.getUsuario().getNombre()).toList();
    }

    private Empleado alta(Long localId, String codigoRol, String nombre, String nombreUsuario) {
        Rol rol = roles.buscarPorCodigo(codigoRol).orElseThrow();
        Usuario usuario = usuarios.guardar(Usuario.nuevoEmpleado(localId, rol, nombre, nombreUsuario,
                nombreUsuario == null ? null : "hashPass", "hashPin"));
        return empleados.guardar(new Empleado(localId, usuario));
    }

    // --- El listado

    @Test
    void el_listado_de_la_seed_de_la_esquina_es_ana_carla_hugo_juan_sin_el_admin() {
        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 0, 20);

        assertThat(nombres(pagina)).containsExactly("Ana", "Carla", "Hugo", "Juan");
        assertThat(pagina.total()).isEqualTo(4);
        assertThat(pagina.contenido()).extracting(e -> e.getUsuario().getRol().getCodigo())
                .containsExactly("MOZO", "CAJA", "COCINA", "MOZO");
    }

    @Test
    void la_primera_pagina_trae_size_empleados_y_la_siguiente_el_resto_en_orden() {
        PaginaDe<Empleado> primera = empleados.listarPagina(esquina(), 0, 2);
        PaginaDe<Empleado> segunda = empleados.listarPagina(esquina(), 1, 2);

        assertThat(nombres(primera)).containsExactly("Ana", "Carla");
        assertThat(nombres(segunda)).containsExactly("Hugo", "Juan");
        assertThat(primera.total()).isEqualTo(4);
        assertThat(segunda.total()).isEqualTo(4);
    }

    @Test
    void una_pagina_mas_alla_del_final_viene_vacia_con_el_total() {
        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 5, 2);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.total()).isEqualTo(4);
    }

    @Test
    void una_pagina_que_no_se_llena_trae_solo_lo_que_hay() {
        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 1, 3);

        assertThat(nombres(pagina)).containsExactly("Juan");
        assertThat(pagina.total()).isEqualTo(4);
    }

    @Test
    void se_ordena_por_nombre_aunque_se_hayan_dado_de_alta_desordenados() {
        alta(esquina(), "CAJA", "Zoe", null);
        alta(esquina(), "COCINA", "Beto", null);
        alta(esquina(), "MOZO", "Abel", "abel");

        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 0, 20);

        assertThat(nombres(pagina)).containsExactly("Abel", "Ana", "Beto", "Carla", "Hugo", "Juan", "Zoe");
        assertThat(pagina.total()).isEqualTo(7);
        assertThat(nombres(empleados.listarPagina(esquina(), 1, 3))).containsExactly("Carla", "Hugo", "Juan");
    }

    @Test
    void con_el_mismo_nombre_el_orden_es_estable_y_no_repite_ni_saltea_entre_paginas() {
        alta(esquina(), "CAJA", "Juan", null);

        List<String> ids = List.of(0, 1, 2, 3, 4).stream()
                .flatMap(p -> empleados.listarPagina(esquina(), p, 1).contenido().stream())
                .map(e -> e.getId().toString())
                .toList();

        assertThat(ids).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void no_mezcla_la_esquina_con_el_puerto() {
        PaginaDe<Empleado> deLaEsquina = empleados.listarPagina(esquina(), 0, 20);
        PaginaDe<Empleado> delPuerto = empleados.listarPagina(puerto(), 0, 20);

        assertThat(nombres(delPuerto)).containsExactly("Juan");
        assertThat(delPuerto.total()).isEqualTo(1);
        assertThat(delPuerto.contenido().get(0).getId()).isEqualTo(empleadoId("Juan", puerto()));
        assertThat(deLaEsquina.contenido()).extracting(Empleado::getLocalId).containsOnly(esquina());
        assertThat(deLaEsquina.contenido()).extracting(Empleado::getId)
                .doesNotContain(empleadoId("Juan", puerto()));
    }

    @Test
    void un_alta_en_el_puerto_no_aparece_en_la_esquina_y_al_reves() {
        alta(puerto(), "CAJA", "Pablo", null);
        alta(esquina(), "CAJA", "Quique", null);

        assertThat(nombres(empleados.listarPagina(puerto(), 0, 20))).containsExactly("Juan", "Pablo");
        assertThat(nombres(empleados.listarPagina(esquina(), 0, 20))).containsExactly("Ana", "Carla", "Hugo", "Juan",
                "Quique");
        assertThat(empleados.listarPagina(puerto(), 0, 20).total()).isEqualTo(2);
    }

    @Test
    void los_dados_de_baja_tambien_salen_marcados() {
        Empleado hugo = empleados.buscarParaModificar(empleadoId("Hugo", esquina()), esquina()).orElseThrow();
        assertThat(hugo.darDeBaja()).isTrue();
        usuarios.guardar(hugo.getUsuario());

        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 0, 20);

        assertThat(nombres(pagina)).containsExactly("Ana", "Carla", "Hugo", "Juan");
        assertThat(pagina.total()).isEqualTo(4);
        assertThat(pagina.contenido()).extracting(Empleado::estaActivo).containsExactly(true, true, false, true);
    }

    @Test
    void otro_admin_dado_de_alta_a_mano_en_el_local_tampoco_se_lista_ni_se_cuenta() {
        // Solo MOZO, CAJA y COCINA: ningún Admin entra, no solo el de la seed.
        Empleado admin2 = alta(esquina(), "ADMIN", "Aaron Admin", null);

        PaginaDe<Empleado> pagina = empleados.listarPagina(esquina(), 0, 20);

        assertThat(nombres(pagina)).containsExactly("Ana", "Carla", "Hugo", "Juan");
        assertThat(pagina.total()).isEqualTo(4);
        assertThat(empleados.buscarPorId(admin2.getId(), esquina())).isEmpty();
    }

    // --- Buscar uno

    @Test
    void buscar_por_id_encuentra_a_juan_de_la_esquina() {
        Long juan = empleadoId("Juan", esquina());

        assertThat(empleados.buscarPorId(juan, esquina())).isPresent()
                .get().extracting(e -> e.getUsuario().getNombreUsuario()).isEqualTo("juan");
        assertThat(empleados.buscarParaModificar(juan, esquina())).isPresent();
    }

    @Test
    void el_admin_de_la_seed_no_se_encuentra_ni_para_ver_ni_para_modificar() {
        Long admin = jdbc.queryForObject("SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id "
                + "JOIN rol r ON r.id = u.rol_id WHERE r.codigo = 'ADMIN' AND e.local_id = ?", Long.class, esquina());

        assertThat(empleados.buscarPorId(admin, esquina())).isEmpty();
        assertThat(empleados.buscarParaModificar(admin, esquina())).isEmpty();
    }

    @Test
    void el_juan_del_puerto_no_se_encuentra_desde_la_esquina_y_al_reves() {
        Long juanPuerto = empleadoId("Juan", puerto());
        Long juanEsquina = empleadoId("Juan", esquina());

        assertThat(empleados.buscarPorId(juanPuerto, esquina())).isEmpty();
        assertThat(empleados.buscarParaModificar(juanPuerto, esquina())).isEmpty();
        assertThat(empleados.buscarPorId(juanEsquina, puerto())).isEmpty();
        assertThat(empleados.buscarPorId(-1L, esquina())).isEmpty();
    }

    @Test
    void un_dado_de_baja_se_sigue_encontrando() {
        Empleado hugo = empleados.buscarParaModificar(empleadoId("Hugo", esquina()), esquina()).orElseThrow();
        hugo.darDeBaja();
        usuarios.guardar(hugo.getUsuario());

        assertThat(empleados.buscarPorId(hugo.getId(), esquina())).isPresent()
                .get().extracting(Empleado::estaActivo).isEqualTo(false);
    }

    // --- Turnos y ausencias del listado

    @Test
    void los_turnos_de_juan_vienen_por_local_y_los_de_otro_local_no() {
        Long juan = empleadoId("Juan", esquina());

        assertThat(empleados.turnosDe(List.of(juan), esquina())).hasSize(7);
        assertThat(empleados.turnosDe(List.of(juan), puerto())).isEmpty();
        assertThat(empleados.turnosDe(List.of(empleadoId("Carla", esquina())), esquina())).isEmpty();
    }

    @Test
    void la_ausencia_vigente_cuenta_desde_el_primer_dia_hasta_el_ultimo_y_sin_fecha_de_vuelta() {
        Empleado ana = empleados.buscarPorId(empleadoId("Ana", esquina()), esquina()).orElseThrow();
        LocalDate hoy = LocalDate.of(2026, 11, 14);
        em.persist(new Ausencia(esquina(), ana, TipoAusencia.VACACIONES, hoy, hoy.plusDays(5), 1L));
        em.flush();

        Long id = ana.getId();
        assertThat(empleados.conAusenciaVigente(List.of(id), esquina(), hoy.minusDays(1))).isEmpty();
        assertThat(empleados.conAusenciaVigente(List.of(id), esquina(), hoy)).containsExactly(id);
        assertThat(empleados.conAusenciaVigente(List.of(id), esquina(), hoy.plusDays(5))).containsExactly(id);
        assertThat(empleados.conAusenciaVigente(List.of(id), esquina(), hoy.plusDays(6))).isEmpty();

        em.persist(new Ausencia(esquina(), ana, TipoAusencia.BAJA_TEMPORAL, hoy.plusDays(10), null, 1L));
        em.flush();
        assertThat(empleados.conAusenciaVigente(List.of(id), esquina(), hoy.plusDays(400))).containsExactly(id);
    }

    @Test
    void la_ausencia_de_ana_no_la_ve_otro_local_ni_marca_a_otros() {
        Empleado ana = empleados.buscarPorId(empleadoId("Ana", esquina()), esquina()).orElseThrow();
        LocalDate hoy = LocalDate.of(2026, 11, 14);
        em.persist(new Ausencia(esquina(), ana, TipoAusencia.FRANCO, hoy, hoy, 1L));
        em.flush();
        Long juan = empleadoId("Juan", esquina());

        assertThat(empleados.conAusenciaVigente(List.of(ana.getId(), juan), esquina(), hoy))
                .containsExactly(ana.getId());
        assertThat(empleados.conAusenciaVigente(List.of(ana.getId()), puerto(), hoy)).isEmpty();
    }

    @Test
    void las_ausencias_vienen_de_la_mas_nueva_a_la_mas_vieja() {
        Empleado ana = empleados.buscarPorId(empleadoId("Ana", esquina()), esquina()).orElseThrow();
        em.persist(new Ausencia(esquina(), ana, TipoAusencia.FRANCO, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2), 1L));
        em.persist(new Ausencia(esquina(), ana, TipoAusencia.VACACIONES, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 10), 1L));
        em.flush();

        assertThat(empleados.ausenciasDe(ana.getId(), esquina())).extracting(Ausencia::getDesde)
                .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 9, 1));
        assertThat(empleados.ausenciasDe(ana.getId(), puerto())).isEmpty();
    }

    @Test
    void sin_ids_no_consulta_y_devuelve_vacio() {
        assertThat(empleados.turnosDe(List.of(), esquina())).isEmpty();
        assertThat(empleados.conAusenciaVigente(Set.of(), esquina(), LocalDate.of(2026, 11, 14))).isEmpty();
    }

    // --- El usuario del mozo

    @Test
    void el_usuario_se_compara_sin_distinguir_mayusculas_dentro_del_local() {
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "juan", null)).isTrue();
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "JUAN", null)).isTrue();
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "Juan", null)).isTrue();
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "pedro", null)).isFalse();
    }

    @Test
    void CP_27_03_el_mismo_usuario_se_puede_repetir_en_el_puerto() {
        Long ana = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre = 'Ana' AND local_id = ?", Long.class,
                esquina());

        // Ana es "ana" en La Esquina: en El Puerto ese usuario está libre, y "juan" ya lo tiene el de allá.
        assertThat(usuarios.existeNombreDeUsuario(puerto(), "ana", null)).isFalse();
        assertThat(usuarios.existeNombreDeUsuario(puerto(), "juan", null)).isTrue();
        Empleado otroJuan = alta(puerto(), "MOZO", "Otro", "ana");
        assertThat(otroJuan.getId()).isNotNull();
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "ana", ana)).isFalse();
    }

    @Test
    void el_propio_usuario_se_excluye_pero_el_de_otro_cuenta() {
        Long juan = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre = 'Juan' AND local_id = ?", Long.class,
                esquina());

        assertThat(usuarios.existeNombreDeUsuario(esquina(), "juan", juan)).isFalse();
        assertThat(usuarios.existeNombreDeUsuario(esquina(), "ana", juan)).isTrue();
    }

    @Test
    void el_usuario_de_un_dado_de_baja_sigue_ocupado() {
        Empleado juan = empleados.buscarParaModificar(empleadoId("Juan", esquina()), esquina()).orElseThrow();
        juan.darDeBaja();
        usuarios.guardar(juan.getUsuario());

        assertThat(usuarios.existeNombreDeUsuario(esquina(), "juan", null)).isTrue();
    }

    @Test
    void guardar_un_usuario_repetido_salta_como_409_aunque_se_escape_de_la_consulta() {
        // Dos altas a la vez: las dos pasan la consulta y la segunda cae en el UNIQUE (local_id, nombre_usuario).
        Rol mozo = roles.buscarPorCodigo("MOZO").orElseThrow();

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> usuarios
                .guardar(Usuario.nuevoEmpleado(esquina(), mozo, "Otro Juan", "JUAN", "hashPass", "hashPin")));

        assertThat(e.getMessage()).isEqualTo("Ya existe un usuario con ese nombre");
    }

    // --- Lo que queda guardado

    @Test
    void CP_27_01_el_alta_de_un_mozo_queda_en_la_base_con_las_marcas() {
        Empleado pedro = alta(esquina(), "MOZO", "Pedro", "pedro");

        assertThat(jdbc.queryForMap("SELECT debe_cambiar_credencial AS d, contrasena_provisoria AS p, activo AS a, "
                + "intentos_fallidos AS i FROM usuario WHERE id = ?", pedro.getUsuario().getId()))
                .containsEntry("d", true).containsEntry("p", true).containsEntry("a", true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM empleado WHERE usuario_id = ? AND local_id = ?",
                Integer.class, pedro.getUsuario().getId(), esquina())).isEqualTo(1);
    }

    @Test
    void el_alta_de_caja_no_queda_con_contrasena_provisoria() {
        Empleado nuevo = alta(esquina(), "CAJA", "Nora", null);

        assertThat(jdbc.queryForObject("SELECT contrasena_provisoria FROM usuario WHERE id = ?", Boolean.class,
                nuevo.getUsuario().getId())).isFalse();
    }

    @Test
    void la_baja_deja_las_filas_y_solo_apaga_activo() {
        Long juan = empleadoId("Juan", esquina());
        int turnosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM turno WHERE empleado_id = ?", Integer.class, juan);
        Empleado e = empleados.buscarParaModificar(juan, esquina()).orElseThrow();
        e.darDeBaja();
        usuarios.guardar(e.getUsuario());
        em.flush();

        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class,
                e.getUsuario().getId())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM empleado WHERE id = ?", Integer.class, juan)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM turno WHERE empleado_id = ?", Integer.class, juan))
                .isEqualTo(turnosAntes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mozo_mesa WHERE empleado_id = ?", Integer.class, juan))
                .isEqualTo(3);
    }

    // --- La V6

    @Test
    void v6_los_usuarios_de_la_seed_quedan_sin_contrasena_provisoria_y_sin_valido_desde() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE contrasena_provisoria = TRUE",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE sesion_valida_desde IS NOT NULL",
                Integer.class)).isZero();
    }

    @Test
    void v6_las_columnas_nuevas_son_contrasena_provisoria_no_nula_y_sesion_valida_desde_nula() {
        List<String> columnas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'usuario' AND "
                + "((column_name = 'contrasena_provisoria' AND is_nullable = 'NO' AND column_default = '0') "
                + "OR (column_name = 'sesion_valida_desde' AND is_nullable = 'YES'))", String.class);

        assertThat(columnas).containsExactlyInAnyOrder("contrasena_provisoria", "sesion_valida_desde");
    }

    @Test
    void v6_flyway_la_aplico_con_exito() {
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '6'",
                Boolean.class)).isTrue();
    }
}
