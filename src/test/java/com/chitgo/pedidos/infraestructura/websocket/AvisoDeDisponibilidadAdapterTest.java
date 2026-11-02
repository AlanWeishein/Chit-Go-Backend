package com.chitgo.pedidos.infraestructura.websocket;

import static com.chitgo.pruebas.Entidades.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.AvisoDeDisponibilidad;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/**
 * El aviso de disponibilidad de HU-09 contra el MySQL del Compose (tiene que estar levantado), con las mesas de la
 * seed. El publicador es un mock: se mira a qué sesiones les llegó el evento. Hace rollback al final.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AvisoDeDisponibilidadAdapterTest {

    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AvisoDeDisponibilidad aviso;

    @MockitoBean
    private PublicadorDeEventos eventos;

    @Test
    void apagar_un_plato_avisa_a_cada_sesion_abierta_del_local_y_a_nadie_mas() {
        Mesa mesa1 = mesaPorToken(MESA_1_ESQUINA);
        Long esquina = mesa1.getLocalId();
        Long milanesa = milanesa(esquina);
        Mesa mesa2 = mesaPorToken(MESA_2_ESQUINA);
        Mesa delPuerto = mesaPorToken(MESA_1_PUERTO);

        Cuenta cerrada = cuenta(mesa1, EstadoCuenta.CERRADA);
        String deLaCerrada = sesion(cerrada);
        Cuenta abierta = cuenta(mesa1, EstadoCuenta.ABIERTA);
        String unCelular = sesion(abierta);
        String otroCelular = sesion(abierta);
        String conLaCuentaPedida = sesion(cuenta(mesa2, EstadoCuenta.SOLICITADA));
        String delOtroLocal = sesion(cuenta(delPuerto, EstadoCuenta.ABIERTA));
        em.flush();

        aviso.platoCambio(esquina, milanesa, false);

        ArgumentCaptor<EventoTiempoReal> evento = ArgumentCaptor.forClass(EventoTiempoReal.class);
        for (String token : List.of(unCelular, otroCelular, conLaCuentaPedida)) {
            verify(eventos).aCliente(eq(token), evento.capture());
        }
        verify(eventos, never()).aCliente(eq(deLaCerrada), any());
        verify(eventos, never()).aCliente(eq(delOtroLocal), any());
        assertThat(evento.getAllValues()).allSatisfy(e -> {
            assertThat(e.tipo()).isEqualTo(TipoEvento.PLATO_BAJA);
            assertThat(e.platoId()).isEqualTo(milanesa);
            assertThat(e.pedidoId()).isNull();
            assertThat(e.ocurridoEn()).isNotNull();
        });
    }

    @Test
    void prender_un_plato_manda_plato_disponible() {
        Mesa mesa = mesaPorToken(MESA_1_ESQUINA);
        Long milanesa = milanesa(mesa.getLocalId());
        String token = sesion(cuenta(mesa, EstadoCuenta.ABIERTA));
        em.flush();

        aviso.platoCambio(mesa.getLocalId(), milanesa, true);

        ArgumentCaptor<EventoTiempoReal> evento = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos).aCliente(eq(token), evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.PLATO_DISPONIBLE);
        assertThat(evento.getValue().platoId()).isEqualTo(milanesa);
    }

    @Test
    void no_avisa_a_las_sesiones_de_la_esquina_si_el_plato_es_del_puerto() {
        Mesa esquina = mesaPorToken(MESA_1_ESQUINA);
        Mesa puerto = mesaPorToken(MESA_1_PUERTO);
        String deLaEsquina = sesion(cuenta(esquina, EstadoCuenta.ABIERTA));
        String delPuerto = sesion(cuenta(puerto, EstadoCuenta.ABIERTA));
        em.flush();

        Long platoDelPuerto = em.createQuery("SELECT p.id FROM Plato p WHERE p.localId = :local", Long.class)
                .setParameter("local", puerto.getLocalId()).setMaxResults(1).getSingleResult();

        aviso.platoCambio(puerto.getLocalId(), platoDelPuerto, false);

        verify(eventos).aCliente(eq(delPuerto), any());
        verify(eventos, never()).aCliente(eq(deLaEsquina), any());
    }

    private Mesa mesaPorToken(String tokenQr) {
        return em.createQuery("SELECT m FROM Mesa m WHERE m.tokenQr = :token", Mesa.class)
                .setParameter("token", tokenQr).getSingleResult();
    }

    private Long milanesa(Long localId) {
        return em.createQuery("SELECT p FROM Plato p WHERE p.localId = :local AND p.nombre = :nombre", Plato.class)
                .setParameter("local", localId).setParameter("nombre", "Milanesa napolitana").getSingleResult()
                .getId();
    }

    private Cuenta cuenta(Mesa mesa, EstadoCuenta estado) {
        Cuenta cuenta = con(new Cuenta(mesa.getLocalId(), mesa, null, Instant.parse("2026-11-05T19:50:00Z")),
                "estado", estado);
        em.persist(cuenta);
        return cuenta;
    }

    private String sesion(Cuenta cuenta) {
        String token = UUID.randomUUID().toString();
        em.persist(new ClienteSesion(cuenta.getLocalId(), cuenta, token, true));
        return token;
    }
}
