package com.chitgo.usuarios.aplicacion;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Corte por inactividad (RNF-14, CU-13 1a): el token dura T minutos y cada request con sesión lo vuelve a emitir.
 * A los T minutos sin actividad vence solo y lo próximo da 401. Lo llama FiltroDeSesion.
 *
 * <p>Los datos salen de la base y no del token que vino: así el T que cambió el Admin, un blanqueo y una baja
 * valen desde el próximo request, y no recién cuando vence el token.
 */
@Service
public class RenovarSesionService {

    private final UsuarioRepository usuarios;
    private final LocalRepository locales;
    private final AperturaDeSesion apertura;

    RenovarSesionService(UsuarioRepository usuarios, LocalRepository locales, AperturaDeSesion apertura) {
        this.usuarios = usuarios;
        this.locales = locales;
        this.apertura = apertura;
    }

    /** @return el token nuevo, o vacío si el usuario ya no existe o está dado de baja: ahí se corta la sesión */
    @Transactional(readOnly = true)
    public Optional<ResultadoDeIngreso> renovar(Long usuarioId) {
        return usuarios.buscarPorId(usuarioId)
                .filter(Usuario::isActivo)
                .map(usuario -> apertura.abrir(usuario, localDe(usuario)));
    }

    /** El local sale de la fila del usuario, como en el login con email. El Gerente/Dueño no tiene. */
    private Local localDe(Usuario usuario) {
        return usuario.getLocalId() != null
                ? locales.buscarPorId(usuario.getLocalId()).orElseThrow()
                : null;
    }
}
