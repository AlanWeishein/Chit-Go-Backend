package com.chitgo.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;

/** El T de RNF-14 según el rol, sin base. El local tiene 20 minutos, como si el Admin lo hubiera cambiado. */
class AperturaDeSesionTest {

    private final Local laEsquina = new Local(1L, "La Esquina", "Calle 1", "token");

    AperturaDeSesionTest() {
        // CU-30 todavía no está: el Admin no tiene cómo cambiarlo.
        ReflectionTestUtils.setField(laEsquina, "minutosInactividadSesion", (short) 20);
    }

    @Test
    void cajaTieneUnaHoraAunqueElLocalDigaOtraCosa() {
        assertThat(AperturaDeSesion.duracionPara(con("CAJA"), laEsquina)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void mozoCocinaYAdminUsanElTDelLocal() {
        for (String rol : new String[] {"MOZO", "COCINA", "ADMIN"}) {
            assertThat(AperturaDeSesion.duracionPara(con(rol), laEsquina)).isEqualTo(Duration.ofMinutes(20));
        }
    }

    @Test
    void elTDelLocalEsDiezPorDefecto() {
        Local nuevo = new Local(1L, "Nuevo", "Calle 2", "otro-token");

        assertThat(AperturaDeSesion.duracionPara(con("MOZO"), nuevo)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void elGerenteDuenoTieneDiezMinutos() {
        assertThat(AperturaDeSesion.duracionPara(con("GERENTE_DUENO"), null)).isEqualTo(Duration.ofMinutes(10));
    }

    private static Usuario con(String rol) {
        return new Usuario(1L, new Rol(rol, rol), "Alguien", null, null, "hash", "hash");
    }
}
