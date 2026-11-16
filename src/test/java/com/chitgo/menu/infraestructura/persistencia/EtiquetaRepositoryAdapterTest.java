package com.chitgo.menu.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.EtiquetaRepetidaException;

/** El choque del UNIQUE de la base, cuando dos Admin crean la misma etiqueta a la vez (decidido el 29/10). */
class EtiquetaRepositoryAdapterTest {

    private final EtiquetaJpaRepository jpa = mock(EtiquetaJpaRepository.class);
    private final EtiquetaRepositoryAdapter adapter = new EtiquetaRepositoryAdapter(jpa);
    private final Etiqueta sinTacc = new Etiqueta(1L, "sin tacc");

    @Test
    void el_choque_del_unique_de_la_etiqueta_es_409_esa_etiqueta_ya_existe() {
        when(jpa.saveAndFlush(sinTacc)).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1-sin tacc' for key 'etiqueta.uq_etiqueta_nombre'")));

        EtiquetaRepetidaException e = assertThrows(EtiquetaRepetidaException.class, () -> adapter.guardar(sinTacc));

        assertEquals("Esa etiqueta ya existe", e.getMessage());
    }

    @Test
    void otra_violacion_de_integridad_sigue_como_esta() {
        DataIntegrityViolationException otra = new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException("Cannot add or update a child row: a foreign key "
                        + "constraint fails (CONSTRAINT `fk_etiqueta_local` FOREIGN KEY (`local_id`))"));
        when(jpa.saveAndFlush(sinTacc)).thenThrow(otra);

        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
                () -> adapter.guardar(sinTacc));

        assertSame(otra, e);
    }
}
