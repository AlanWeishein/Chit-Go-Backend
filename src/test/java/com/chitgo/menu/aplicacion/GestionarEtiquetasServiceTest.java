package com.chitgo.menu.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.aplicacion.dto.EtiquetaAdminDto;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.EtiquetaRepetidaException;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class GestionarEtiquetasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T20:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(2L, "ADMIN", 1L, false, AHORA.plusSeconds(600));

    private final EtiquetaRepository etiquetas = mock(EtiquetaRepository.class);
    private final Auditor auditor = mock(Auditor.class);
    private Etiqueta sinTacc;
    private Etiqueta vegano;
    private GestionarEtiquetasService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarEtiquetasService(etiquetas, auditor);
        sinTacc = conId(new Etiqueta(1L, "Sin TACC"), 1L);
        vegano = conId(new Etiqueta(1L, "Vegano"), 2L);
        when(etiquetas.buscarPorId(1L, 1L)).thenReturn(Optional.of(sinTacc));
        when(etiquetas.buscarPorId(2L, 1L)).thenReturn(Optional.of(vegano));
        when(etiquetas.guardar(any(Etiqueta.class))).thenAnswer(inv -> {
            Etiqueta etiqueta = inv.getArgument(0);
            return etiqueta.getId() == null ? conId(etiqueta, 40L) : etiqueta;
        });
    }

    @Test
    void listar_trae_las_del_local() {
        when(etiquetas.listarDelLocal(1L)).thenReturn(List.of(sinTacc, vegano));

        assertEquals(List.of(new EtiquetaAdminDto(1L, "Sin TACC"), new EtiquetaAdminDto(2L, "Vegano")),
                servicio.listar(ADMIN));
    }

    @Test
    void crear_guarda_sin_espacios_en_las_puntas_y_audita_crear_etiqueta() {
        EtiquetaAdminDto dto = servicio.crear(ADMIN, " Sin lactosa ");

        assertEquals(new EtiquetaAdminDto(40L, "Sin lactosa"), dto);
        verify(etiquetas).existeConNombre(1L, "Sin lactosa", null);
        verify(auditor).registrar(1L, 2L, "CREAR_ETIQUETA", "ETIQUETA", 40L, null, Map.of("nombre", "Sin lactosa"));
    }

    @Test
    void crear_sin_nombre_es_400_y_no_guarda_ni_audita() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(ADMIN, ""));

        assertEquals("Poné un nombre", e.getMessage());
        verify(etiquetas, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_25_05_crear_una_que_ya_existe_la_frena_la_busqueda_con_409() {
        // La base compara con ai_ci: "sin tacc" encuentra a "Sin TACC".
        when(etiquetas.existeConNombre(1L, "sin tacc", null)).thenReturn(true);

        EtiquetaRepetidaException e = assertThrows(EtiquetaRepetidaException.class,
                () -> servicio.crear(ADMIN, "sin tacc"));

        assertEquals("Esa etiqueta ya existe", e.getMessage());
        verify(etiquetas, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_25_05_si_otro_admin_la_crea_a_la_vez_la_frena_el_unique_con_el_mismo_409() {
        // La búsqueda no la encontró, pero otro Admin la guardó justo antes: el adaptador pasa el choque del UNIQUE.
        when(etiquetas.existeConNombre(eq(1L), anyString(), isNull())).thenReturn(false);
        when(etiquetas.guardar(any(Etiqueta.class))).thenThrow(new EtiquetaRepetidaException());

        EtiquetaRepetidaException e = assertThrows(EtiquetaRepetidaException.class,
                () -> servicio.crear(ADMIN, "Sin lactosa"));

        assertEquals("Esa etiqueta ya existe", e.getMessage());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_audita_el_nombre_anterior_y_el_nuevo() {
        EtiquetaAdminDto dto = servicio.renombrar(ADMIN, 2L, "Vegana");

        assertEquals(new EtiquetaAdminDto(2L, "Vegana"), dto);
        verify(etiquetas).existeConNombre(1L, "Vegana", 2L);
        verify(etiquetas).guardar(vegano);
        verify(auditor).registrar(1L, 2L, "EDITAR_ETIQUETA", "ETIQUETA", 2L, Map.of("nombre", "Vegano"),
                Map.of("nombre", "Vegana"));
    }

    @Test
    void renombrar_a_una_que_ya_existe_es_409_y_deja_el_nombre_que_tenia() {
        when(etiquetas.existeConNombre(1L, "sin tacc", 2L)).thenReturn(true);

        EtiquetaRepetidaException e = assertThrows(EtiquetaRepetidaException.class,
                () -> servicio.renombrar(ADMIN, 2L, "sin tacc"));

        assertEquals("Esa etiqueta ya existe", e.getMessage());
        assertEquals("Vegano", vegano.getNombre());
        verify(etiquetas, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_y_que_lo_frene_el_unique_es_el_mismo_409_sin_auditar() {
        when(etiquetas.guardar(vegano)).thenThrow(new EtiquetaRepetidaException());

        assertThrows(EtiquetaRepetidaException.class, () -> servicio.renombrar(ADMIN, 2L, "Sin lactosa"));

        verifyNoInteractions(auditor);
    }

    @Test
    void renombrarla_a_una_variante_de_su_propio_nombre_vale_porque_no_se_cuenta_a_si_misma() {
        EtiquetaAdminDto dto = servicio.renombrar(ADMIN, 1L, "Sin Tacc");

        assertEquals(new EtiquetaAdminDto(1L, "Sin Tacc"), dto);
        verify(etiquetas).existeConNombre(1L, "Sin Tacc", 1L);
        verify(auditor).registrar(1L, 2L, "EDITAR_ETIQUETA", "ETIQUETA", 1L, Map.of("nombre", "Sin TACC"),
                Map.of("nombre", "Sin Tacc"));
    }

    @Test
    void renombrar_con_el_mismo_nombre_no_busca_ni_guarda_ni_audita() {
        servicio.renombrar(ADMIN, 2L, " Vegano");

        verify(etiquetas, never()).existeConNombre(any(), any(), any());
        verify(etiquetas, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_sin_nombre_es_400_y_deja_el_que_tenia() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.renombrar(ADMIN, 2L, "  "));

        assertEquals("Poné un nombre", e.getMessage());
        assertEquals("Vegano", vegano.getNombre());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_una_de_otro_local_da_404_como_una_que_no_existe() {
        when(etiquetas.buscarPorId(9L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException e = assertThrows(NoEncontradoException.class,
                () -> servicio.renombrar(ADMIN, 9L, "Picante"));

        assertEquals("La etiqueta no existe", e.getMessage());
        verifyNoInteractions(auditor);
    }

    @Test
    void borrar_la_borra_y_audita_borrar_etiqueta() {
        servicio.borrar(ADMIN, 2L);

        verify(etiquetas).borrar(vegano);
        verify(auditor).registrar(1L, 2L, "BORRAR_ETIQUETA", "ETIQUETA", 2L, Map.of("nombre", "Vegano"), null);
    }

    @Test
    void borrar_una_de_otro_local_da_404() {
        when(etiquetas.buscarPorId(9L, 1L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> servicio.borrar(ADMIN, 9L));

        verify(etiquetas, never()).borrar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_entra() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(9L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.crear(gerente, "Picante"));
        verifyNoInteractions(etiquetas, auditor);
    }
}
