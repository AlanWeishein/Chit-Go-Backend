package com.chitgo.pedidos.infraestructura.persistencia;

import static com.chitgo.pruebas.Entidades.con;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
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
import com.chitgo.menu.dominio.puertos.PedidosConPlato;
import com.chitgo.menu.dominio.puertos.PedidosConPlato.PedidoAfectado;
import com.chitgo.menu.dominio.puertos.PedidosConPlato.PlatoDelPedido;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;

/**
 * La consulta de los pedidos afectados de HU-09 contra el MySQL del Compose (tiene que estar levantado), con las
 * mesas y los platos de la seed. Hace rollback al final: la base queda como estaba.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PedidosConPlatoAdapterTest {

    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PedidosConPlato pedidosConPlato;

    @Test
    void trae_los_pendientes_y_los_aprobados_con_el_plato_y_todos_sus_items_en_orden_de_llegada() {
        Mesa mesa = mesa(MESA_1_ESQUINA);
        Long esquina = mesa.getLocalId();
        Plato milanesa = plato(esquina, "Milanesa napolitana");
        Plato flan = plato(esquina, "Flan casero");
        ClienteSesion sesion = sesion(mesa);

        Pedido aprobado = pedido(sesion, "2026-11-05T20:02:00Z", EstadoPedido.APROBADO);
        item(aprobado, milanesa, 1);
        Pedido conFlan = pedido(sesion, "2026-11-05T20:01:00Z", EstadoPedido.PENDIENTE_APROBACION);
        item(conFlan, flan, 1);
        item(conFlan, milanesa, 2);
        Pedido primero = pedido(sesion, "2026-11-05T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        item(primero, milanesa, 1);
        Pedido soloFlan = pedido(sesion, "2026-11-05T19:59:00Z", EstadoPedido.PENDIENTE_APROBACION);
        item(soloFlan, flan, 1);
        persistirTodo(aprobado, conFlan, primero, soloFlan);

        List<PedidoAfectado> afectados = deEstaPrueba(pedidosConPlato.afectadosPor(milanesa.getId(), esquina),
                aprobado, conFlan, primero, soloFlan);

        assertThat(afectados).containsExactly(
                new PedidoAfectado(primero.getId(), "1", "PENDIENTE_APROBACION",
                        List.of(new PlatoDelPedido(milanesa.getId(), "Milanesa napolitana", 1))),
                new PedidoAfectado(conFlan.getId(), "1", "PENDIENTE_APROBACION", List.of(
                        new PlatoDelPedido(flan.getId(), "Flan casero", 1),
                        new PlatoDelPedido(milanesa.getId(), "Milanesa napolitana", 2))),
                new PedidoAfectado(aprobado.getId(), "1", "APROBADO",
                        List.of(new PlatoDelPedido(milanesa.getId(), "Milanesa napolitana", 1))));
    }

    @Test
    void no_trae_los_que_ya_no_se_pueden_cancelar() {
        Mesa mesa = mesa(MESA_1_ESQUINA);
        Long esquina = mesa.getLocalId();
        Plato milanesa = plato(esquina, "Milanesa napolitana");
        ClienteSesion sesion = sesion(mesa);
        List<EstadoPedido> noCancelables = List.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO,
                EstadoPedido.SERVIDO, EstadoPedido.CANCELADO, EstadoPedido.RECHAZADO);
        Pedido[] pedidos = noCancelables.stream()
                .map(estado -> {
                    Pedido p = pedido(sesion, "2026-11-05T20:00:00Z", estado);
                    item(p, milanesa, 1);
                    return p;
                })
                .toArray(Pedido[]::new);
        persistirTodo(pedidos);

        assertThat(deEstaPrueba(pedidosConPlato.afectadosPor(milanesa.getId(), esquina), pedidos)).isEmpty();
    }

    @Test
    void no_mezcla_los_pedidos_de_otro_local() {
        Mesa mesaPuerto = mesa(MESA_1_PUERTO);
        Long puerto = mesaPuerto.getLocalId();
        Mesa mesaEsquina = mesa(MESA_1_ESQUINA);
        Long esquina = mesaEsquina.getLocalId();
        Plato platoPuerto = em.createQuery("SELECT p FROM Plato p WHERE p.localId = :local", Plato.class)
                .setParameter("local", puerto).setMaxResults(1).getSingleResult();
        Plato milanesa = plato(esquina, "Milanesa napolitana");

        Pedido delPuerto = pedido(sesion(mesaPuerto), "2026-11-05T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        item(delPuerto, platoPuerto, 1);
        Pedido deLaEsquina = pedido(sesion(mesaEsquina), "2026-11-05T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        item(deLaEsquina, milanesa, 1);
        persistirTodo(delPuerto, deLaEsquina);

        assertThat(pedidosConPlato.afectadosPor(platoPuerto.getId(), esquina)).isEmpty();
        assertThat(pedidosConPlato.afectadosPor(milanesa.getId(), puerto)).isEmpty();
        assertThat(pedidosConPlato.afectadosPor(platoPuerto.getId(), puerto)).extracting(PedidoAfectado::id)
                .contains(delPuerto.getId()).doesNotContain(deLaEsquina.getId());
        assertThat(pedidosConPlato.afectadosPor(milanesa.getId(), esquina)).extracting(PedidoAfectado::id)
                .contains(deLaEsquina.getId()).doesNotContain(delPuerto.getId());
    }

    /** Solo los pedidos que armó este test, por si la base tiene otros. */
    private static List<PedidoAfectado> deEstaPrueba(List<PedidoAfectado> afectados, Pedido... pedidos) {
        List<Long> ids = Arrays.stream(pedidos).map(Pedido::getId).toList();
        return afectados.stream().filter(a -> ids.contains(a.id())).toList();
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
        Cuenta cuenta = new Cuenta(mesa.getLocalId(), mesa, null, Instant.parse("2026-11-05T19:50:00Z"));
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

    private static void item(Pedido pedido, Plato plato, int cantidad) {
        new ItemPedido(pedido.getLocalId(), pedido, plato.getId(), (short) cantidad, plato.getPrecio(), null);
    }

    /** Los ítems se guardan en cascada. El flush deja todo en la base para que la consulta lo lea. */
    private void persistirTodo(Pedido... pedidos) {
        for (Pedido p : pedidos) {
            em.persist(p);
        }
        em.flush();
    }
}
