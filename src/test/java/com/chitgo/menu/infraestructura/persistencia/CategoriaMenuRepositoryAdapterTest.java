package com.chitgo.menu.infraestructura.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.chitgo.menu.dominio.CategoriaConPlatosException;
import com.chitgo.menu.dominio.CategoriaMenu;

/** La FK de plato.categoria_id, cuando entra un plato justo mientras se borra su categoría. */
class CategoriaMenuRepositoryAdapterTest {

    private final CategoriaMenuJpaRepository jpa = mock(CategoriaMenuJpaRepository.class);
    private final CategoriaMenuRepositoryAdapter adapter = new CategoriaMenuRepositoryAdapter(jpa);
    private final CategoriaMenu entradas = new CategoriaMenu(1L, "Entradas", (short) 3);

    @Test
    void borrar_hace_el_delete_ya_para_que_la_fk_salte_aca() {
        adapter.borrar(entradas);

        verify(jpa).delete(entradas);
        verify(jpa).flush();
    }

    @Test
    void si_la_fk_de_plato_frena_el_borrado_es_409() {
        doThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException("Cannot delete or update a parent row: a foreign key "
                        + "constraint fails (`chitgo`.`plato`, CONSTRAINT `fk_plato_categoria` FOREIGN KEY "
                        + "(`categoria_id`) REFERENCES `categoria_menu` (`id`))")))
                .when(jpa).flush();

        CategoriaConPlatosException e = assertThrows(CategoriaConPlatosException.class, () -> adapter.borrar(entradas));

        assertEquals("La categoría tiene platos. Movelos a otra antes de borrarla", e.getMessage());
    }

    @Test
    void otra_violacion_de_integridad_sigue_como_esta() {
        DataIntegrityViolationException otra = new DataIntegrityViolationException("otra",
                new SQLIntegrityConstraintViolationException("CONSTRAINT `fk_otra`"));
        doThrow(otra).when(jpa).flush();

        assertSame(otra, assertThrows(DataIntegrityViolationException.class, () -> adapter.borrar(entradas)));
    }
}
