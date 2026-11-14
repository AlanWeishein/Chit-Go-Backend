package com.chitgo.pedidos.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.pedidos.aplicacion.dto.MesaSinMozoDto;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;
import com.chitgo.usuarios.dominio.puertos.TurnosQueEmpiezan;

/**
 * HU-14: las mesas habilitadas que se quedan sin mozo de turno (CP-07-05). Al empezar un turno se avisa por
 * {@code /alertas}, con el mismo evento y el mismo texto que el aviso de EntrarALaMesaService al abrir la cuenta,
 * así el front no distingue de dónde vino. No se guarda un Aviso, porque Aviso pide una cuenta y a esa hora la mesa
 * puede no tener ninguna: al reconectar, Caja y el Admin piden la lista con el GET, que la recalcula (RNF-08).
 */
@Service
public class MesasSinMozoService {

    private final TurnosQueEmpiezan turnosQueEmpiezan;
    private final MesaRepository mesas;
    private final MozoDeTurno mozoDeTurno;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    public MesasSinMozoService(TurnosQueEmpiezan turnosQueEmpiezan, MesaRepository mesas, MozoDeTurno mozoDeTurno,
            PublicadorDeEventos eventos, Reloj reloj) {
        this.turnosQueEmpiezan = turnosQueEmpiezan;
        this.mesas = mesas;
        this.mozoDeTurno = mozoDeTurno;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * La pasada de cada minuto. Solo mira los locales donde empieza un turno en este minuto: en un minuto sin
     * turnos que empiecen no consulta ninguna mesa. Un turno que empieza avisa una vez, porque en el minuto
     * siguiente ya no está empezando.
     */
    @Transactional(readOnly = true)
    public void avisarAlEmpezarLosTurnos() {
        Instant ahora = reloj.ahora();
        Set<Long> locales = turnosQueEmpiezan.localesConTurnoQueEmpieza(ahora);
        for (Long localId : locales) {
            for (Mesa mesa : sinMozo(localId, ahora)) {
                eventos.aAlertas(localId, EventoTiempoReal.de(TipoEvento.MESA_SIN_MOZO, null, mesa.getId(), null,
                        "La mesa " + mesa.getNumero() + " no tiene mozo de turno", ahora));
            }
        }
    }

    /** GET /alertas/mesas-sin-mozo: las del local de la sesión, ahora, por número. */
    @Transactional(readOnly = true)
    public List<MesaSinMozoDto> listar(UsuarioAutenticado usuario) {
        return sinMozo(localDe(usuario), reloj.ahora()).stream()
                .map(m -> new MesaSinMozoDto(m.getId(), m.getNumero()))
                .toList();
    }

    /** Las inhabilitadas no avisan. Se pregunta mesa por mesa, con la misma regla que al abrir la cuenta. */
    private List<Mesa> sinMozo(Long localId, Instant ahora) {
        return mesas.listarDelLocal(localId).stream()
                .filter(m -> !m.isInhabilitada())
                .filter(m -> mozoDeTurno.buscarParaMesa(m.getId(), ahora).isEmpty())
                .toList();
    }

    /** El Gerente/Dueño no tiene local fijo. */
    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) {
            throw new AccesoDenegadoException("Esta acción es de un local");
        }
        return usuario.localId();
    }
}
