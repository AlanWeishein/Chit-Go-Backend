package com.chitgo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.auditoria.dominio.RegistroAuditoria;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.PlatoEtiqueta;
import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.TipoAviso;
import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.MozoMesa;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.TipoAusencia;
import com.chitgo.usuarios.dominio.Turno;
import com.chitgo.usuarios.dominio.Usuario;

/**
 * Inserta un local con todas sus tablas y lo vuelve a leer desde la base.
 * Corre contra el MySQL del Compose (tiene que estar levantado) y hace rollback al final.
 * Con el perfil dev, porque si la base ya tiene la seed aplicada, sin db/dev Flyway no valida.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class GrafoCompletoPersistenciaTest {

    @PersistenceContext
    private EntityManager em;

    @Test
    void insertaYLeeElGrafoCompletoConLocalIdEnLasTablasPuente() {
        // Roles de la V2
        Rol gerenteDueno = rol("GERENTE_DUENO");
        Rol mozo = rol("MOZO");
        Rol admin = rol("ADMIN");
        assertThat(mozo.getPermisos()).hasSize(3);

        // Usuario y local
        Usuario dueno = persistir(new Usuario(null, gerenteDueno, "Dueño", null,
                "dueno-" + UUID.randomUUID() + "@chitgo.test", "hash", null));
        Local local = persistir(new Local(dueno.getId(), "Local de prueba", "Calle 123", UUID.randomUUID().toString()));
        Long localId = local.getId();
        Usuario usuarioAdmin = persistir(new Usuario(localId, admin, "Admin", null,
                "admin-" + UUID.randomUUID() + "@chitgo.test", "hash", null));
        Usuario usuarioMozo = persistir(new Usuario(localId, mozo, "Mozo", "mozo1", null, "hash", "pin"));

        // Personal
        Empleado empleado = persistir(new Empleado(localId, usuarioMozo));
        persistir(new Turno(localId, empleado, DiaSemana.LUN, LocalTime.of(9, 0), LocalTime.of(17, 0)));
        persistir(new Ausencia(localId, empleado, TipoAusencia.FRANCO, LocalDate.now(), null, usuarioAdmin.getId()));

        // Mesas
        Mesa mesa = persistir(new Mesa(localId, "1", UUID.randomUUID().toString()));
        new MozoMesa(localId, empleado, mesa.getId());

        // Carta
        CategoriaMenu categoria = persistir(new CategoriaMenu(localId, "Principales", (short) 1));
        Etiqueta sinTacc = persistir(new Etiqueta(localId, "Sin TACC"));
        Plato plato = persistir(new Plato(localId, categoria, "Milanesa", null, new BigDecimal("1500.00"), null));
        new PlatoEtiqueta(localId, plato, sinTacc);

        // Cuenta, pedido y ticket
        Cuenta cuenta = persistir(new Cuenta(localId, mesa, empleado.getId(), Instant.now()));
        ClienteSesion sesion = persistir(new ClienteSesion(localId, cuenta, UUID.randomUUID().toString(), true));
        Pedido pedido = new Pedido(localId, cuenta, sesion, UUID.randomUUID().toString(), Instant.now());
        new ItemPedido(localId, pedido, plato.getId(), (short) 2, plato.getPrecio(), "sin sal");
        persistir(pedido);
        persistir(new Ticket(localId, cuenta, Instant.now(), new BigDecimal("3000.00")));

        // Avisos y auditoría
        persistir(new Aviso(localId, mesa, TipoAviso.LLAMAR_MOZO));
        persistir(new RegistroAuditoria(localId, usuarioAdmin.getId(), "CREAR_PLATO", "PLATO", plato.getId(), null, "{}"));

        em.flush();
        em.clear();

        // Se lee de nuevo desde la base
        Empleado empleadoLeido = em.find(Empleado.class, empleado.getId());
        assertThat(empleadoLeido.getMesasAsignadas())
                .singleElement()
                .satisfies(mm -> {
                    assertThat(mm.getLocalId()).isEqualTo(localId);
                    assertThat(mm.getMesaId()).isEqualTo(mesa.getId());
                });
        assertThat(empleadoLeido.getMesaIds()).containsExactly(mesa.getId());

        Plato platoLeido = em.find(Plato.class, plato.getId());
        assertThat(platoLeido.getPlatoEtiquetas())
                .singleElement()
                .satisfies(pe -> {
                    assertThat(pe.getLocalId()).isEqualTo(localId);
                    assertThat(pe.getEtiqueta().getNombre()).isEqualTo("Sin TACC");
                });

        Pedido pedidoLeido = em.find(Pedido.class, pedido.getId());
        assertThat(pedidoLeido.getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getLocalId()).isEqualTo(localId);
                    assertThat(item.getPlatoId()).isEqualTo(plato.getId());
                    assertThat(item.getCantidad()).isEqualTo((short) 2);
                });

        // Y que la columna local_id quedó escrita en las tres tablas
        assertThat(localIdEn("item_pedido", "pedido_id", pedido.getId())).isEqualTo(localId);
        assertThat(localIdEn("mozo_mesa", "empleado_id", empleado.getId())).isEqualTo(localId);
        assertThat(localIdEn("plato_etiqueta", "plato_id", plato.getId())).isEqualTo(localId);
    }

    private Rol rol(String codigo) {
        return em.createQuery("select r from Rol r where r.codigo = :codigo", Rol.class)
                .setParameter("codigo", codigo)
                .getSingleResult();
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }

    private Long localIdEn(String tabla, String columna, Long id) {
        Number localId = (Number) em.createNativeQuery("select local_id from " + tabla + " where " + columna + " = ?")
                .setParameter(1, id)
                .getSingleResult();
        return localId.longValue();
    }
}
