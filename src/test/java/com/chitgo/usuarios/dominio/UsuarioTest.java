package com.chitgo.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** El bloqueo por intentos fallidos (RNF-07), sin base. */
class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-10-16T12:00:00Z");

    private final Usuario juan = new Usuario(1L, new Rol("MOZO", "Mozo"), "Juan", "juan", null, "hash", "hash");

    @Test
    void sinIntentosNoEstaBloqueado() {
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
        assertThat(juan.getBloqueadoHasta()).isNull();
    }

    @Test
    void cuatroIntentosFallidosNoLoBloquean() {
        fallar(4);

        assertThat(juan.getIntentosFallidos()).isEqualTo((short) 4);
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void alQuintoIntentoFallidoQuedaBloqueadoSinVencimiento() {
        fallar(5);

        assertThat(juan.getIntentosFallidos()).isEqualTo((short) 5);
        assertThat(juan.getBloqueadoHasta()).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO);
        assertThat(juan.estaBloqueado(AHORA)).isTrue();
        // No vence solo: sigue bloqueado aunque pasen años.
        assertThat(juan.estaBloqueado(AHORA.plus(Duration.ofDays(365 * 10)))).isTrue();
    }

    @Test
    void entrarBienVuelveLosIntentosACero() {
        // CP-10-05
        fallar(2);

        juan.registrarIngreso();

        assertThat(juan.getIntentosFallidos()).isZero();
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void elBloqueoEntraEnUnTimestampDeMysql() {
        assertThat(Usuario.BLOQUEO_SIN_VENCIMIENTO).isBefore(Instant.parse("2038-01-19T03:14:07Z"));
    }

    @Test
    void entranConPinSoloMozoCajaYCocina() {
        assertThat(new Rol("MOZO", "Mozo").entraConPin()).isTrue();
        assertThat(new Rol("CAJA", "Caja").entraConPin()).isTrue();
        assertThat(new Rol("COCINA", "Cocina").entraConPin()).isTrue();
        assertThat(new Rol("ADMIN", "Admin").entraConPin()).isFalse();
        assertThat(new Rol("GERENTE_DUENO", "Gerente/Dueño").entraConPin()).isFalse();
    }

    @Test
    void entranConEmailSoloElAdminYElGerenteDueno() {
        assertThat(new Rol("ADMIN", "Admin").entraConEmail()).isTrue();
        assertThat(new Rol("GERENTE_DUENO", "Gerente/Dueño").entraConEmail()).isTrue();
        assertThat(new Rol("MOZO", "Mozo").entraConEmail()).isFalse();
        assertThat(new Rol("CAJA", "Caja").entraConEmail()).isFalse();
        assertThat(new Rol("COCINA", "Cocina").entraConEmail()).isFalse();
    }

    @Test
    void soloElMozoEntraConUsuarioYContrasenaCadaSieteDias() {
        assertThat(new Rol("MOZO", "Mozo").esMozo()).isTrue();
        assertThat(new Rol("CAJA", "Caja").esMozo()).isFalse();
        assertThat(new Rol("COCINA", "Cocina").esMozo()).isFalse();
        assertThat(new Rol("ADMIN", "Admin").esMozo()).isFalse();
        assertThat(new Rol("GERENTE_DUENO", "Gerente/Dueño").esMozo()).isFalse();
    }

    @Test
    void elegirLaCredencialApagaLaMarca() {
        // Un hash NULL deja el que tenía: el mozo que después de un blanqueo solo elige el PIN.
        Usuario pedro = new Usuario(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", null, "contrasena", "pin");
        assertThat(pedro.isDebeCambiarCredencial()).isTrue();

        pedro.elegirCredencial("pinNuevo", null);

        assertThat(pedro.isDebeCambiarCredencial()).isFalse();
        assertThat(pedro.getPinHash()).isEqualTo("pinNuevo");
        assertThat(pedro.getHashPassword()).isEqualTo("contrasena");
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            juan.registrarIntentoFallido();
        }
    }

    // --- HU-14: alta, edición y baja de empleados

    @Test
    void unUsuarioComunNaceSinContrasenaProvisoria() {
        assertThat(juan.isContrasenaProvisoria()).isFalse();
        assertThat(juan.isActivo()).isTrue();
    }

    @Test
    void elAltaDeUnMozoLoDejaConCambioObligatorioYContrasenaProvisoria() {
        // CP-27-01
        Usuario pedro = Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hashPass", "hashPin");

        assertThat(pedro.isDebeCambiarCredencial()).isTrue();
        assertThat(pedro.isContrasenaProvisoria()).isTrue();
        assertThat(pedro.isActivo()).isTrue();
        assertThat(pedro.getLocalId()).isEqualTo(1L);
        assertThat(pedro.getNombreUsuario()).isEqualTo("pedro");
        assertThat(pedro.getHashPassword()).isEqualTo("hashPass");
        assertThat(pedro.getPinHash()).isEqualTo("hashPin");
        assertThat(pedro.getEmail()).isNull();
    }

    @Test
    void elAltaDeCajaYCocinaTieneCambioObligatorioPeroNoContrasenaProvisoria() {
        for (String codigo : new String[] {"CAJA", "COCINA"}) {
            Usuario nuevo = Usuario.nuevoEmpleado(1L, new Rol(codigo, codigo), "Nuevo", null, null, "hashPin");

            assertThat(nuevo.isDebeCambiarCredencial()).as(codigo).isTrue();
            assertThat(nuevo.isContrasenaProvisoria()).as(codigo).isFalse();
            assertThat(nuevo.getNombreUsuario()).as(codigo).isNull();
            assertThat(nuevo.getHashPassword()).as(codigo).isNull();
        }
    }

    @Test
    void renombrarCambiaElNombreYElUsuarioYNadaMas() {
        fallar(3);

        juan.renombrar("Juan Pablo", "juanpa");

        assertThat(juan.getNombre()).isEqualTo("Juan Pablo");
        assertThat(juan.getNombreUsuario()).isEqualTo("juanpa");
        assertThat(juan.getRol().getCodigo()).isEqualTo("MOZO");
        assertThat(juan.getHashPassword()).isEqualTo("hash");
        assertThat(juan.getPinHash()).isEqualTo("hash");
        assertThat(juan.getIntentosFallidos()).isEqualTo((short) 3);
        assertThat(juan.isActivo()).isTrue();
    }

    @Test
    void darDeBajaLoDesactivaYLaSegundaVezNoCambiaNada() {
        // CP-27-04
        assertThat(juan.darDeBaja()).isTrue();
        assertThat(juan.isActivo()).isFalse();

        assertThat(juan.darDeBaja()).isFalse();
        assertThat(juan.isActivo()).isFalse();
    }

    @Test
    void darDeBajaNoTocaLasCredencialesNiElBloqueo() {
        fallar(5);

        juan.darDeBaja();

        assertThat(juan.getPinHash()).isEqualTo("hash");
        assertThat(juan.getHashPassword()).isEqualTo("hash");
        assertThat(juan.getBloqueadoHasta()).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO);
    }

    // --- HU-14: blanqueo del PIN y contraseña provisoria

    @Test
    void elBlanqueoDestrabaAlBloqueadoYDejaElPinProvisorio() {
        // CP-10-06
        fallar(5);
        juan.elegirCredencial("pinElegido", null);

        juan.blanquearPin("pinProvisorio");

        assertThat(juan.getPinHash()).isEqualTo("pinProvisorio");
        assertThat(juan.getIntentosFallidos()).isZero();
        assertThat(juan.getBloqueadoHasta()).isNull();
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
        assertThat(juan.isDebeCambiarCredencial()).isTrue();
    }

    @Test
    void elBlanqueoNoTocaLaContrasenaNiLaContrasenaProvisoria() {
        Usuario pedro = Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hashPass", "hashPin");
        juan.blanquearPin("pinProvisorio");
        pedro.blanquearPin("pinProvisorio");

        // Juan ya había elegido su contraseña: el blanqueo no se la vuelve a pedir.
        assertThat(juan.isContrasenaProvisoria()).isFalse();
        assertThat(juan.getHashPassword()).isEqualTo("hash");
        // Pedro todavía no la había elegido: sigue provisoria.
        assertThat(pedro.isContrasenaProvisoria()).isTrue();
        assertThat(pedro.getHashPassword()).isEqualTo("hashPass");
    }

    @Test
    void elegirLaContrasenaApagaLaContrasenaProvisoria() {
        Usuario pedro = Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hashPass", "hashPin");

        pedro.elegirCredencial("pinNuevo", "passNueva");

        assertThat(pedro.isContrasenaProvisoria()).isFalse();
        assertThat(pedro.isDebeCambiarCredencial()).isFalse();
        assertThat(pedro.getHashPassword()).isEqualTo("passNueva");
    }

    @Test
    void elegirSoloElPinDejaLaContrasenaProvisoriaComoEstaba() {
        Usuario pedro = Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hashPass", "hashPin");

        pedro.elegirCredencial("pinNuevo", null);

        assertThat(pedro.isContrasenaProvisoria()).isTrue();
    }
}
