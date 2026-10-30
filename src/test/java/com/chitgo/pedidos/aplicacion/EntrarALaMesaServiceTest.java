package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.EstadoDeMesaDto;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeSesion;
import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.TipoAviso;
import com.chitgo.pedidos.dominio.puertos.AvisoRepository;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;

class EntrarALaMesaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T20:00:00Z");
    private static final String QR = "qr-mesa-1";

    private final MesaRepository mesas = mock(MesaRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final ClienteSesionRepository sesiones = mock(ClienteSesionRepository.class);
    private final AvisoRepository avisos = mock(AvisoRepository.class);
    private final MozoDeTurno mozoDeTurno = mock(MozoDeTurno.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Mesa mesa = conId(new Mesa(1L, "1", QR), 3L);
    private EntrarALaMesaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new EntrarALaMesaService(mesas, cuentas, sesiones, avisos, mozoDeTurno, carta, eventos,
                new RelojDePrueba(AHORA));
        when(mesas.buscarPorTokenQr(QR)).thenReturn(Optional.of(mesa));
        when(mesas.buscarPorTokenQrParaAbrirCuenta(QR)).thenReturn(Optional.of(mesa));
        when(cuentas.buscarNoCerradaDeMesa(3L)).thenReturn(Optional.empty());
        when(cuentas.guardar(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sesiones.guardar(any(ClienteSesion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mozoDeTurno.buscarParaMesa(eq(3L), any())).thenReturn(Optional.of(5L));
    }

    private Cuenta cuentaAbierta() {
        return conId(new Cuenta(1L, mesa, 5L, AHORA), 7L);
    }

    @Test
    void CP_01_01_el_primero_que_escanea_abre_la_cuenta_y_queda_como_iniciador() {
        ResultadoDeSesion resultado = servicio.abrirSesion(QR, null);

        assertTrue(resultado.sesion().esIniciador());
        assertTrue(resultado.nueva());
        verify(cuentas).guardar(any(Cuenta.class));
        verify(avisos, never()).guardar(any());
    }

    @Test
    void el_que_escanea_despues_entra_en_modo_lectura() {
        when(cuentas.buscarNoCerradaDeMesa(3L)).thenReturn(Optional.of(cuentaAbierta()));

        ResultadoDeSesion resultado = servicio.abrirSesion(QR, null);

        assertFalse(resultado.sesion().esIniciador());
        verify(cuentas, never()).guardar(any());
    }

    @Test
    void el_iniciador_que_vuelve_a_escanear_sigue_siendo_el_iniciador() {
        Cuenta cuenta = cuentaAbierta();
        when(cuentas.buscarNoCerradaDeMesa(3L)).thenReturn(Optional.of(cuenta));
        when(sesiones.buscarPorToken("mi-token"))
                .thenReturn(Optional.of(conId(new ClienteSesion(1L, cuenta, "mi-token", true), 10L)));

        ResultadoDeSesion resultado = servicio.abrirSesion(QR, "mi-token");

        assertTrue(resultado.sesion().esIniciador());
        assertFalse(resultado.nueva());
        assertEquals("mi-token", resultado.sesion().tokenSesion());
        verify(sesiones, never()).guardar(any());
    }

    @Test
    void un_token_de_otra_cuenta_no_cuenta_como_sesion_vigente() {
        when(cuentas.buscarNoCerradaDeMesa(3L)).thenReturn(Optional.of(cuentaAbierta()));
        Cuenta otraCuenta = conId(new Cuenta(1L, mesa, 5L, AHORA), 99L);
        when(sesiones.buscarPorToken("viejo"))
                .thenReturn(Optional.of(conId(new ClienteSesion(1L, otraCuenta, "viejo", true), 11L)));

        ResultadoDeSesion resultado = servicio.abrirSesion(QR, "viejo");

        assertFalse(resultado.sesion().esIniciador()); // la cuenta ya tiene iniciador: entra de lectura
        assertTrue(resultado.nueva());
    }

    @Test
    void sin_mozo_de_turno_la_cuenta_se_abre_igual_y_se_avisa() {
        when(mozoDeTurno.buscarParaMesa(eq(3L), any())).thenReturn(Optional.empty());

        servicio.abrirSesion(QR, null);

        verify(cuentas).guardar(any(Cuenta.class));
        verify(avisos).guardar(any(Aviso.class));
        verify(eventos).aAlertas(eq(1L), any(EventoTiempoReal.class));
    }

    @Test
    void con_mozo_de_turno_la_cuenta_queda_a_su_nombre() {
        servicio.abrirSesion(QR, null);
        verify(avisos, never()).guardar(any());
        verify(eventos, never()).aAlertas(any(), any());
    }

    @Test
    void una_mesa_inhabilitada_sin_cuenta_no_abre() {
        con(mesa, "inhabilitada", true);
        assertThrows(ReglaDeNegocioException.class, () -> servicio.abrirSesion(QR, null));
        verify(cuentas, never()).guardar(any());
    }

    @Test
    void un_token_de_qr_que_no_existe_da_404() {
        when(mesas.buscarPorTokenQrParaAbrirCuenta("otro")).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, () -> servicio.abrirSesion("otro", null));
    }

    @Test
    void la_consulta_de_una_mesa_inhabilitada_dice_que_no_esta_disponible() {
        con(mesa, "inhabilitada", true);
        EstadoDeMesaDto estado = servicio.consultar(QR);
        assertFalse(estado.disponible());
        assertEquals("1", estado.numero());
    }
}
