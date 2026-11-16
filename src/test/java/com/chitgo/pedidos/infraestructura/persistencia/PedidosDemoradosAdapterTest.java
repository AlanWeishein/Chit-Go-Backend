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

import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados.PedidoPendiente;

/**
 * La consulta de la alerta de los 3 minutos contra el MySQL del Compose (tiene que estar levantado), con las
 * mesas de la seed. Hace rollback al final: la base queda como estaba.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PedidosDemoradosAdapterTest {

    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private PedidosDemorados pedidosDemorados;

    @Test
    void trae_los_pendientes_con_despues_de_excluido_y_hasta_incluido() {
        ClienteSesion sesion = sesion(mesa(MESA_1_ESQUINA));
        Pedido enElInicio = pedido(sesion, "2026-10-20T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido adentro = pedido(sesion, "2026-10-20T20:00:01Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido enElFinal = pedido(sesion, "2026-10-20T20:00:05Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido despues = pedido(sesion, "2026-10-20T20:00:06Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido aprobado = pedido(sesion, "2026-10-20T20:00:03Z", EstadoPedido.APROBADO);
        persistirTodo(enElInicio, adentro, enElFinal, despues, aprobado);

        List<Long> ids = idsDeEstaPrueba(
                pedidosDemorados.pendientesEnviadosEntre(Instant.parse("2026-10-20T20:00:00Z"),
                        Instant.parse("2026-10-20T20:00:05Z")),
                enElInicio, adentro, enElFinal, despues, aprobado);

        assertThat(ids).containsExactly(adentro.getId(), enElFinal.getId());
    }

    /** enviado_en es TIMESTAMP sin fracción y la hora del reloj sí trae milisegundos. */
    @Test
    void con_milisegundos_en_los_bordes_compara_igual_que_en_java() {
        ClienteSesion sesion = sesion(mesa(MESA_1_ESQUINA));
        Pedido antes = pedido(sesion, "2026-10-20T20:00:00Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido adentro = pedido(sesion, "2026-10-20T20:00:01Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido despues = pedido(sesion, "2026-10-20T20:00:02Z", EstadoPedido.PENDIENTE_APROBACION);
        persistirTodo(antes, adentro, despues);

        List<Long> ids = idsDeEstaPrueba(
                pedidosDemorados.pendientesEnviadosEntre(Instant.parse("2026-10-20T20:00:00.500Z"),
                        Instant.parse("2026-10-20T20:00:01.500Z")),
                antes, adentro, despues);

        assertThat(ids).containsExactly(adentro.getId());
    }

    @Test
    void trae_los_de_todos_los_locales_con_su_local_y_su_mesa() {
        Mesa mesaEsquina = mesa(MESA_1_ESQUINA);
        Mesa mesaPuerto = mesa(MESA_1_PUERTO);
        Pedido deLaEsquina = pedido(sesion(mesaEsquina), "2026-10-20T20:00:01Z", EstadoPedido.PENDIENTE_APROBACION);
        Pedido delPuerto = pedido(sesion(mesaPuerto), "2026-10-20T20:00:02Z", EstadoPedido.PENDIENTE_APROBACION);
        persistirTodo(deLaEsquina, delPuerto);

        List<PedidoPendiente> deEstaPrueba = pedidosDemorados.pendientesEnviadosEntre(
                        Instant.parse("2026-10-20T20:00:00Z"), Instant.parse("2026-10-20T20:00:05Z")).stream()
                .filter(p -> List.of(deLaEsquina.getId(), delPuerto.getId()).contains(p.id()))
                .toList();

        assertThat(deEstaPrueba).containsExactly(
                new PedidoPendiente(deLaEsquina.getId(), mesaEsquina.getLocalId(), mesaEsquina.getId()),
                new PedidoPendiente(delPuerto.getId(), mesaPuerto.getLocalId(), mesaPuerto.getId()));
    }

    private static List<Long> idsDeEstaPrueba(List<PedidoPendiente> encontrados, Pedido... deEstaPrueba) {
        List<Long> ids = Arrays.stream(deEstaPrueba).map(Pedido::getId).toList();
        return encontrados.stream().map(PedidoPendiente::id).filter(ids::contains).toList();
    }

    private Mesa mesa(String tokenQr) {
        return em.createQuery("SELECT m FROM Mesa m WHERE m.tokenQr = :token", Mesa.class)
                .setParameter("token", tokenQr).getSingleResult();
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

    /** El flush deja todo en la base para que la consulta lo lea. */
    private void persistirTodo(Pedido... pedidos) {
        for (Pedido p : pedidos) {
            em.persist(p);
        }
        em.flush();
    }
}
