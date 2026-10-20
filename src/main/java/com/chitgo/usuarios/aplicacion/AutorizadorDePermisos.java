package com.chitgo.usuarios.aplicacion;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.usuarios.dominio.Permiso;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Controla en el servidor que el usuario de la sesión tenga el permiso de la acción (rol_permiso). El token
 * lleva el rol y no los permisos, así que se leen de la base: si cambian, vale lo nuevo. Ocultar un botón en
 * el front no alcanza.
 */
@Service
public class AutorizadorDePermisos {

    static final String SIN_PERMISO = "No tenés permiso para hacer esto.";

    private final UsuarioRepository usuarios;

    public AutorizadorDePermisos(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Verdadero si el usuario está activo y su rol tiene alguno de esos permisos. */
    @Transactional(readOnly = true)
    public boolean tieneAlguno(UsuarioAutenticado sesion, String... codigos) {
        Set<String> permisos = usuarios.buscarPorId(sesion.usuarioId())
                .filter(Usuario::isActivo)
                .map(u -> u.getRol().getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toSet()))
                .orElse(Set.of());
        for (String codigo : codigos) {
            if (permisos.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    /** @throws AccesoDenegadoException si no tiene ninguno de esos permisos (responde 403) */
    @Transactional(readOnly = true)
    public void exigirAlguno(UsuarioAutenticado sesion, String... codigos) {
        if (!tieneAlguno(sesion, codigos)) {
            throw new AccesoDenegadoException(SIN_PERMISO);
        }
    }
}
