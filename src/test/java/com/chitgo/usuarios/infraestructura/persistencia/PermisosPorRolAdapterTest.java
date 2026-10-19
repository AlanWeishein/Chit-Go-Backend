package com.chitgo.usuarios.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.chitgo.usuarios.dominio.puertos.PermisosPorRol;

/**
 * Los permisos que el adaptador leyó al arrancar, contra los de V2__roles_y_permisos.sql en el MySQL del Compose.
 * Con el perfil dev, como GrafoCompletoPersistenciaTest, porque con la seed aplicada Flyway no valida sin db/dev.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PermisosPorRolAdapterTest {

    @Autowired
    private PermisosPorRol permisosPorRol;

    @Test
    void cajaTieneExactamenteSusCincoPermisos() {
        assertThat(permisosPorRol.de("CAJA")).containsExactlyInAnyOrder(
                "plato.cambiar_disponibilidad", "pedido.cancelar", "pedido.aprobar_rechazar", "pedido.priorizar",
                "cuenta.cerrar");
    }

    @Test
    void cocinaTieneSoloElSuyo() {
        assertThat(permisosPorRol.de("COCINA")).containsExactly("pedido.cambiar_estado_cocina");
    }

    @Test
    void unRolQueNoExisteNoTieneNingunPermiso() {
        assertThat(permisosPorRol.de("NO_EXISTE")).isNotNull().isEmpty();
    }
}
