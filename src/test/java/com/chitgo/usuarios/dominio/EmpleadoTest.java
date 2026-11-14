package com.chitgo.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** HU-14: lo que el Empleado delega en su Usuario, sin base. */
class EmpleadoTest {

    private static Empleado empleadoDe(String codigoRol) {
        Usuario usuario = Usuario.nuevoEmpleado(1L, new Rol(codigoRol, codigoRol), "Pedro", "pedro", "hash", "hash");
        return new Empleado(1L, usuario);
    }

    @Test
    void losRolesGestionablesSonMozoCajaYCocinaYNoElAdmin() {
        assertThat(Empleado.ROLES_GESTIONABLES).containsExactlyInAnyOrder("MOZO", "CAJA", "COCINA");
        assertThat(Empleado.ROLES_GESTIONABLES).doesNotContain("ADMIN", "GERENTE_DUENO");
    }

    @Test
    void unEmpleadoNuevoTieneLocalUsuarioYNingunaMesa() {
        Empleado pedro = empleadoDe("MOZO");

        assertThat(pedro.getLocalId()).isEqualTo(1L);
        assertThat(pedro.getUsuario().getNombre()).isEqualTo("Pedro");
        assertThat(pedro.getMesaIds()).isEmpty();
        assertThat(pedro.estaActivo()).isTrue();
    }

    @Test
    void renombrarCambiaElNombreYElUsuarioDelUsuario() {
        Empleado pedro = empleadoDe("MOZO");

        pedro.renombrar("Pedro Gomez", "pgomez");

        assertThat(pedro.getUsuario().getNombre()).isEqualTo("Pedro Gomez");
        assertThat(pedro.getUsuario().getNombreUsuario()).isEqualTo("pgomez");
    }

    @Test
    void darDeBajaDesactivaAlUsuarioYLaSegundaVezDiceQueNoCambioNada() {
        Empleado pedro = empleadoDe("CAJA");

        assertThat(pedro.darDeBaja()).isTrue();
        assertThat(pedro.estaActivo()).isFalse();
        assertThat(pedro.getUsuario().isActivo()).isFalse();

        assertThat(pedro.darDeBaja()).isFalse();
        assertThat(pedro.estaActivo()).isFalse();
    }

    @Test
    void darDeBajaNoQuitaLasMesasAsignadas() {
        Empleado pedro = empleadoDe("MOZO");
        new MozoMesa(1L, pedro, 5L);

        pedro.darDeBaja();

        assertThat(pedro.getMesaIds()).containsExactly(5L);
    }

    @Test
    void reemplazarMesasSacaLasQueNoEstanYSumaLasNuevasConElLocalDelEmpleado() {
        Empleado pedro = empleadoDe("MOZO");
        new MozoMesa(1L, pedro, 1L);
        new MozoMesa(1L, pedro, 2L);

        assertThat(pedro.reemplazarMesas(Set.of(2L, 5L))).isTrue();

        assertThat(pedro.getMesaIds()).containsExactlyInAnyOrder(2L, 5L);
        assertThat(pedro.getMesasAsignadas()).extracting(MozoMesa::getLocalId).containsOnly(1L);
    }

    @Test
    void reemplazarMesasPorLasMismasDiceQueNoCambioNada() {
        Empleado pedro = empleadoDe("MOZO");
        new MozoMesa(1L, pedro, 3L);

        assertThat(pedro.reemplazarMesas(Set.of(3L))).isFalse();
        assertThat(pedro.getMesaIds()).containsExactly(3L);
    }

    @Test
    void reemplazarMesasConNingunaLoDejaSinMesas() {
        Empleado pedro = empleadoDe("MOZO");
        new MozoMesa(1L, pedro, 3L);

        assertThat(pedro.reemplazarMesas(Set.of())).isTrue();
        assertThat(pedro.getMesaIds()).isEmpty();
        assertThat(empleadoDe("MOZO").reemplazarMesas(Set.of())).isFalse();
    }
}
