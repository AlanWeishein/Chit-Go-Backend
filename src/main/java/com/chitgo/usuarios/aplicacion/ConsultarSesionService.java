package com.chitgo.usuarios.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.SinSesionException;
import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.aplicacion.dto.SesionDto.LocalDeSesionDto;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Quién está logueado, para el front, que no puede leer la cookie. No renueva el token: consultar la sesión
 * no cuenta como actividad.
 */
@Service
public class ConsultarSesionService {

    private final UsuarioRepository usuarios;
    private final LocalRepository locales;

    public ConsultarSesionService(UsuarioRepository usuarios, LocalRepository locales) {
        this.usuarios = usuarios;
        this.locales = locales;
    }

    /** El rol, debeCambiarCredencial y el vencimiento salen del token; el nombre y el local, de la base. */
    @Transactional(readOnly = true)
    public SesionDto consultar(UsuarioAutenticado sesion) {
        Usuario usuario = usuarios.buscarPorId(sesion.usuarioId())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new SinSesionException("El usuario no existe o está dado de baja"));

        LocalDeSesionDto local = null;
        if (sesion.localId() != null) {
            local = locales.buscarPorId(sesion.localId())
                    .map(l -> new LocalDeSesionDto(l.getId(), l.getNombre()))
                    .orElseThrow(() -> new SinSesionException("El local de la sesión no existe"));
        }

        return new SesionDto(usuario.getId(), usuario.getNombre(), sesion.rol(), local,
                sesion.debeCambiarCredencial(), sesion.expiraEn());
    }
}
