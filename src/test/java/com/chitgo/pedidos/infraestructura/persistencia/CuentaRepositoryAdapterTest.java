package com.chitgo.pedidos.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;

/**
 * Las consultas de la cuenta que dependen de la base (el filtro por local y la versión forzada de la opción A de
 * ST-09) contra el MySQL del Compose (tiene que estar levantado), con las mesas de la seed. Hace rollback al
 * final: la base queda como estaba.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CuentaRepositoryAdapterTest {

    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private CuentaRepository cuentas;

    @Test
    void subir_la_version_la_sube_en_la_base_aunque_la_cuenta_no_cambie() {
        Cuenta cuenta = cuentaNueva(mesa(MESA_1_ESQUINA));
        int antes = cuenta.getVersion();

        cuentas.subirVersion(cuenta);

        assertThat(versionEnLaBase(cuenta)).isEqualTo(antes + 1);

        // Con la cuenta releída (versión nueva) se puede subir otra vez.
        em.clear();
        Cuenta releida = cuentas.buscarPorId(cuenta.getId(), cuenta.getLocalId()).orElseThrow();
        assertThat(releida.getVersion()).isEqualTo(antes + 1);
        cuentas.subirVersion(releida);
        assertThat(versionEnLaBase(releida)).isEqualTo(antes + 2);
    }

    @Test
    void subir_la_version_con_una_version_vieja_es_un_choque_y_no_cambia_la_base() {
        Cuenta cuenta = cuentaNueva(mesa(MESA_1_ESQUINA));
        int antes = cuenta.getVersion();
        // Otro request (el cierre, o un envío) la cambió y confirmó después de que esta la leyó.
        em.createNativeQuery("UPDATE cuenta SET version = version + 1 WHERE id = ?")
                .setParameter(1, cuenta.getId()).executeUpdate();

        assertThrows(ConflictoDeConcurrenciaException.class, () -> cuentas.subirVersion(cuenta));

        assertThat(versionEnLaBase(cuenta)).isEqualTo(antes + 1);
    }

    @Test
    void buscar_por_id_filtra_por_local_y_una_de_otro_local_es_como_una_que_no_existe() {
        Mesa mesaEsquina = mesa(MESA_1_ESQUINA);
        Long esquina = mesaEsquina.getLocalId();
        Long puerto = mesa(MESA_1_PUERTO).getLocalId();
        Cuenta cuenta = cuentaNueva(mesaEsquina);

        assertThat(cuentas.buscarPorId(cuenta.getId(), esquina)).containsSame(cuenta);
        assertThat(cuentas.buscarPorId(cuenta.getId(), puerto)).isEmpty();
        assertThat(cuentas.buscarPorId(cuenta.getId() + 100_000L, esquina)).isEmpty();
    }

    private Cuenta cuentaNueva(Mesa mesa) {
        Cuenta cuenta = new Cuenta(mesa.getLocalId(), mesa, null, Instant.parse("2026-10-30T19:00:00Z"));
        em.persist(cuenta);
        em.flush();
        return cuenta;
    }

    private Mesa mesa(String tokenQr) {
        return em.createQuery("SELECT m FROM Mesa m WHERE m.tokenQr = :token", Mesa.class)
                .setParameter("token", tokenQr).getSingleResult();
    }

    private int versionEnLaBase(Cuenta cuenta) {
        em.flush();
        return ((Number) em.createNativeQuery("SELECT version FROM cuenta WHERE id = ?")
                .setParameter(1, cuenta.getId()).getSingleResult()).intValue();
    }
}
