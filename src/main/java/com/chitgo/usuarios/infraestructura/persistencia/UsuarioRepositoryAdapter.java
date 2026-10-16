package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

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
    public List<Usuario> listarActivosDelLocal(Long localId, Collection<String> codigosDeRol) {
        return jpa.findByLocalIdAndActivoTrueAndRolCodigoInOrderByNombreAsc(localId, codigosDeRol);
    }
}
