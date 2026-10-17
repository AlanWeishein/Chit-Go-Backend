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

/**
 * Lo que hacen todos los logins cuando la credencial ya está verificada, y la renovación de cada request:
 * emitir el token con el T de RNF-14 y armar la sesión.
 */
@Component
class AperturaDeSesion {

    /** Caja se corta a la hora (09/10), con el aviso a los 55 minutos en el front. No la configura el Admin. */
    static final Duration DURACION_CAJA = Duration.ofHours(1);

    /** El Gerente/Dueño no tiene local, y por lo tanto no tiene el T que configura el Admin. */
    static final Duration DURACION_SIN_LOCAL = Duration.ofMinutes(10);

    private final EmisorDeTokens emisor;

    AperturaDeSesion(EmisorDeTokens emisor) {
        this.emisor = emisor;
    }

    /** @param local NULL para el Gerente/Dueño */
    ResultadoDeIngreso abrir(Usuario usuario, Local local) {
        Duration duracion = duracionPara(usuario, local);
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

    /** El T según el rol: Caja, una hora; el resto, el del local (10 por defecto); el Gerente/Dueño, 10. */
    static Duration duracionPara(Usuario usuario, Local local) {
        if (usuario.getRol().esCaja()) {
            return DURACION_CAJA;
        }
        return local != null ? Duration.ofMinutes(local.getMinutosInactividadSesion()) : DURACION_SIN_LOCAL;
    }
}
