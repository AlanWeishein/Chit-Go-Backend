package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;

import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

@Repository
class UsuarioRepositoryAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository jpa;

    UsuarioRepositoryAdapter(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Usuario> buscarPorIdParaIngreso(Long id) {
        return jpa.buscarConBloqueo(id);
    }

    @Override
    public Optional<Usuario> buscarPorEmailParaIngreso(String email) {
        return jpa.buscarPorEmailConBloqueo(email);
    }

    @Override
    public Optional<Usuario> buscarPorNombreDeUsuarioParaIngreso(Long localId, String nombreUsuario) {
        return jpa.buscarPorNombreDeUsuarioConBloqueo(localId, nombreUsuario);
    }

    /**
     * Con flush, para que el UNIQUE (local_id, nombre_usuario) salte acá y no al cerrar la transacción: si dos
     * altas con el mismo usuario llegan juntas, la segunda también da el 409. Toda violación de integridad se
     * traduce a ese mensaje: en el alta y la edición de empleados la única unique que puede saltar es la del
     * nombre de usuario (el email va en NULL).
     */
    @Override
    public Usuario guardar(Usuario usuario) {
        try {
            return jpa.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaDeNegocioException("Ya existe un usuario con ese nombre");
        }
    }

    @Override
    public boolean existeNombreDeUsuario(Long localId, String nombreUsuario, Long excluirUsuarioId) {
        return jpa.existeNombreDeUsuario(localId, nombreUsuario, excluirUsuarioId == null ? -1L : excluirUsuarioId);
    }

    @Override
    public List<Usuario> listarActivosDelLocal(Long localId, Collection<String> codigosDeRol) {
        return jpa.findByLocalIdAndActivoTrueAndRolCodigoInOrderByNombreAsc(localId, codigosDeRol);
    }
}
