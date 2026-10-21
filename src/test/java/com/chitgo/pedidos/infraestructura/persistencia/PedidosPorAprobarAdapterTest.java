package com.chitgo.pedidos.infraestructura.persistencia;

import static com.chitgo.pruebas.Entidades.con;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar.ItemPorAprobar;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar.PedidoPorAprobar;

/**
 * La consulta con JOIN de la cola de Caja contra el MySQL del Compose (tiene que estar levantado), con las
 * mesas y los platos de la seed. Hace rollback al final: la base queda como estaba.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PedidosPorAprobarAdapterTest {

    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PedidosPorAprobar pedidosPorAprobar;

    @Test
    void trae_los_pendientes_del_local_con_la_mesa_y_los_platos_en_orden_de_llegada() {
        Mesa mesa = mesa(MESA_1_ESQUINA);
        Long esquina = mesa.getLocalId();
        Plato milanesa = plato(esquina, "Milanesa napolitana");
        Plato ensalada = plato(esquina, "Ensalada mixta");
        con(ensalada, "activo", false); // HU-09 todavía no existe: se apaga a mano, y el rollback lo vuelve

        ClienteSesion sesion = sesion(mesa);
        Pedido segundo = pedido(sesion, "2026-10-20T20:01:00Z", EstadoPedido.PENDIENTE_APROBACION);
        new ItemPedido(esquina, segundo, milanesa.getId(), (short) 1, milanesa.getPrecio(), null);
        Pedido primero = pedido(sesion, "2026-10-20T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        new ItemPedido(esquina, primero, milanesa.getId(), (short) 2, milanesa.getPrecio(), "sin cebolla");
        new ItemPedido(esquina, primero, ensalada.getId(), (short) 1, ensalada.getPrecio(), null);
        Pedido aprobado = pedido(sesion, "2026-10-20T19:59:00Z", EstadoPedido.APROBADO);
        new ItemPedido(esquina, aprobado, milanesa.getId(), (short) 1, milanesa.getPrecio(), null);
        persistirTodo(segundo, primero, aprobado);

        List<PedidoPorAprobar> deEstaPrueba = pedidosPorAprobar.pendientesDelLocal(esquina).stream()
                .filter(p -> List.of(primero.getId(), segundo.getId(), aprobado.getId()).contains(p.id()))
                .toList();

        assertThat(deEstaPrueba).extracting(PedidoPorAprobar::id).containsExactly(primero.getId(), segundo.getId());
        PedidoPorAprobar p = deEstaPrueba.get(0);
        assertThat(p.mesa()).isEqualTo("1");
        assertThat(p.enviadoEn()).isEqualTo(Instant.parse("2026-10-20T20:00:00Z"));
        assertThat(p.items()).containsExactly(
                new ItemPorAprobar(milanesa.getId(), "Milanesa napolitana", 2, "sin cebolla", true),
                new ItemPorAprobar(ensalada.getId(), "Ensalada mixta", 1, null, false));
    }

    @Test
    void los_pendientes_de_otro_local_no_aparecen() {
        Mesa mesaPuerto = mesa(MESA_1_PUERTO);
        Long puerto = mesaPuerto.getLocalId();
        Long esquina = mesa(MESA_1_ESQUINA).getLocalId();
        Plato platoPuerto = em.createQuery("SELECT p FROM Plato p WHERE p.localId = :local", Plato.class)
                .setParameter("local", puerto).setMaxResults(1).getSingleResult();

        Pedido delPuerto = pedido(sesion(mesaPuerto), "2026-10-20T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        new ItemPedido(puerto, delPuerto, platoPuerto.getId(), (short) 1, platoPuerto.getPrecio(), null);
        persistirTodo(delPuerto);

        assertThat(pedidosPorAprobar.pendientesDelLocal(esquina)).extracting(PedidoPorAprobar::id)
                .doesNotContain(delPuerto.getId());
        assertThat(pedidosPorAprobar.pendientesDelLocal(puerto)).extracting(PedidoPorAprobar::id)
                .contains(delPuerto.getId());
    }

    private Mesa mesa(String tokenQr) {
        return em.createQuery("SELECT m FROM Mesa m WHERE m.tokenQr = :token", Mesa.class)
                .setParameter("token", tokenQr).getSingleResult();
    }

    private Plato plato(Long localId, String nombre) {
        return em.createQuery("SELECT p FROM Plato p WHERE p.localId = :local AND p.nombre = :nombre", Plato.class)
                .setParameter("local", localId).setParameter("nombre", nombre).getSingleResult();
    }

    private ClienteSesion sesion(Mesa mesa) {
        Cuenta cuenta = new Cuenta(mesa.getLocalId(), mesa, null, Instant.parse("2026-10-20T19:50:00Z"));
        em.persist(cuenta);
        ClienteSesion sesion = new ClienteSesion(mesa.getLocalId(), cuenta, UUID.randomUUID().toString(), true);
        em.persist(sesion);
        return sesion;
    }

    private static Pedido pedido(ClienteSesion sesion, String enviadoEn, EstadoPedido estado) {
        Pedido pedido = new Pedido(sesion.getLocalId(), sesion.getCuenta(), sesion, UUID.randomUUID().toString(),
                Instant.parse(enviadoEn));
        return con(pedido, "estado", estado);
    }

    /** Los ítems se guardan en cascada. El flush deja todo en la base para que la consulta lo lea. */
    private void persistirTodo(Pedido... pedidos) {
        for (Pedido p : pedidos) {
            em.persist(p);
        }
        em.flush();
    }
}
