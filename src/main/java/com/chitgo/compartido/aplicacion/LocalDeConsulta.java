package com.chitgo.compartido.aplicacion;

import org.springframework.stereotype.Component;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

/** Admin: su local. Gerente/Dueño: comprueba la propiedad antes de consultar. */
@Component
public class LocalDeConsulta {
    private final LocalRepository locales;
    public LocalDeConsulta(LocalRepository locales) { this.locales = locales; }

    public Long resolver(UsuarioAutenticado usuario, Long localId) {
        if (usuario.localId() != null) {
            if (localId != null && !localId.equals(usuario.localId())) {
                throw new NoEncontradoException("El local no existe");
            }
            return usuario.localId();
        }
        if (!"GERENTE_DUENO".equals(usuario.rol())) throw new AccesoDenegadoException("Esta acción es de un local");
        return locales.buscarPorId(localId == null ? -1L : localId)
                .filter(l -> l.getPropietarioUsuarioId().equals(usuario.usuarioId()))
                .orElseThrow(() -> new NoEncontradoException("El local no existe")).getId();
    }
}
