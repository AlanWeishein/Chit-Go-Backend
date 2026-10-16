package com.chitgo.usuarios.dominio.puertos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.chitgo.usuarios.dominio.Usuario;

/** Sin filtro por local: el login busca por email en toda la base (ver entidades-jpa-soluciones). */
public interface UsuarioRepository {

    Optional<Usuario> buscarPorId(Long id);

    /**
     * Con la fila bloqueada hasta el fin de la transacción: dos intentos al mismo tiempo no pisan el contador
     * de intentos fallidos.
     */
    Optional<Usuario> buscarPorIdParaIngreso(Long id);

    /** Los activos del local con alguno de esos roles, ordenados por nombre. */
    List<Usuario> listarActivosDelLocal(Long localId, Collection<String> codigosDeRol);
}
