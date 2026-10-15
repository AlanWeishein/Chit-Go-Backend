package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Usuario;

/** Sin filtro por local: el login busca por email en toda la base (ver entidades-jpa-soluciones). */
public interface UsuarioRepository {

    Optional<Usuario> buscarPorId(Long id);
}
