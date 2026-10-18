package com.chitgo.usuarios.aplicacion;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.usuarios.aplicacion.dto.DispositivoDto;
import com.chitgo.usuarios.dominio.DispositivoHabilitado;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Qué mozo habilitó este dispositivo, para el front, que no puede leer la cookie. Con eso muestra P-32 antes
 * del PIN cuando hace falta (CP-10-11).
 */
@Service
public class ConsultarDispositivoService {

    private final EmisorDeHabilitaciones emisor;
    private final UsuarioRepository usuarios;

    public ConsultarDispositivoService(EmisorDeHabilitaciones emisor, UsuarioRepository usuarios) {
        this.emisor = emisor;
        this.usuarios = usuarios;
    }

    /**
     * @param habilitacion el valor de la cookie de dispositivo, o NULL si no vino
     * @return vacío si no hay cookie, si no sirve o venció, o si el mozo ya no está activo
     */
    @Transactional(readOnly = true)
    public Optional<DispositivoDto> consultar(String habilitacion) {
        return leer(habilitacion).flatMap(habilitado -> usuarios.buscarPorId(habilitado.usuarioId())
                .filter(Usuario::isActivo)
                .filter(u -> u.getRol().esMozo())
                .map(u -> new DispositivoDto(u.getId(), u.getNombre(), habilitado.venceEn())));
    }

    /** Vacío si no vino la cookie o no sirve: para el que llama es lo mismo que no tenerla. */
    private Optional<DispositivoHabilitado> leer(String habilitacion) {
        if (habilitacion == null || habilitacion.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(emisor.leer(habilitacion));
        } catch (TokenInvalidoException e) {
            return Optional.empty();
        }
    }
}
