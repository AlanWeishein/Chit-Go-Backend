package com.chitgo.usuarios.aplicacion;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
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

    private final EmisorDeTokens emisor;
    private final ReglasDelNegocio reglas;

    AperturaDeSesion(EmisorDeTokens emisor, ReglasDelNegocio reglas) {
        this.emisor = emisor;
        this.reglas = reglas;
    }

    /** @param local NULL para el Gerente/Dueño */
    ResultadoDeIngreso abrir(Usuario usuario, Local local) {
        Duration duracion = duracionPara(usuario, local, reglas);
        Long localId = local != null ? local.getId() : null;
        DatosDeSesion datos = new DatosDeSesion(usuario.getId(), usuario.getRol().getCodigo(), localId,
                usuario.isDebeCambiarCredencial());
        String token = emisor.emitir(datos, duracion);

        // El vencimiento tal como quedó en el token (en segundos), el mismo que va a dar /auth/sesion.
        Instant expiraEn = emisor.leer(token).expiraEn();
        LocalDeSesionDto localDto = local != null ? new LocalDeSesionDto(local.getId(), local.getNombre()) : null;
        SesionDto sesion = new SesionDto(usuario.getId(), usuario.getNombre(), datos.rol(), localDto,
                datos.debeCambiarCredencial(), usuario.isContrasenaProvisoria(), expiraEn);
        return new ResultadoDeIngreso(token, duracion, sesion);
    }

    /**
     * El T según el rol. Caja, {@code sesion-de-caja} (una hora por defecto, 09/10), que no la configura el Admin.
     * El resto, el del local (10 por defecto, en la base). El Gerente/Dueño no tiene local, y por lo tanto no
     * tiene el T que configura el Admin: {@code sesion-sin-local} (10 minutos por defecto).
     */
    static Duration duracionPara(Usuario usuario, Local local, ReglasDelNegocio reglas) {
        if (usuario.getRol().esCaja()) {
            return reglas.sesionDeCaja();
        }
        return local != null ? Duration.ofMinutes(local.getMinutosInactividadSesion()) : reglas.sesionSinLocal();
    }
}
