package com.chitgo.usuarios.aplicacion;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.aplicacion.dto.SesionDto.LocalDeSesionDto;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;

/** Lo que hacen todos los logins cuando la credencial ya está verificada: emitir el token y armar la sesión. */
@Component
class AperturaDeSesion {

    /** El Gerente/Dueño no tiene local, y por lo tanto no tiene el T que configura el Admin. */
    static final Duration DURACION_SIN_LOCAL = Duration.ofMinutes(10);

    private final EmisorDeTokens emisor;

    AperturaDeSesion(EmisorDeTokens emisor) {
        this.emisor = emisor;
    }

    /** @param local NULL para el Gerente/Dueño */
    ResultadoDeIngreso abrir(Usuario usuario, Local local) {
        // Tarea 7: T según el rol (Caja, 60 minutos). Por ahora, el del local para todos los que tienen uno.
        Duration duracion = local != null
                ? Duration.ofMinutes(local.getMinutosInactividadSesion())
                : DURACION_SIN_LOCAL;
        Long localId = local != null ? local.getId() : null;
        DatosDeSesion datos = new DatosDeSesion(usuario.getId(), usuario.getRol().getCodigo(), localId,
                usuario.isDebeCambiarCredencial());
        String token = emisor.emitir(datos, duracion);

        // El vencimiento tal como quedó en el token (en segundos), el mismo que va a dar /auth/sesion.
        Instant expiraEn = emisor.leer(token).expiraEn();
        LocalDeSesionDto localDto = local != null ? new LocalDeSesionDto(local.getId(), local.getNombre()) : null;
        SesionDto sesion = new SesionDto(usuario.getId(), usuario.getNombre(), datos.rol(), localDto,
                datos.debeCambiarCredencial(), expiraEn);
        return new ResultadoDeIngreso(token, duracion, sesion);
    }
}
