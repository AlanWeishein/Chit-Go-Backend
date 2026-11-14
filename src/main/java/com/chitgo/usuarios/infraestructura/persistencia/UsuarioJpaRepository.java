package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.usuarios.dominio.Usuario;

interface UsuarioJpaRepository extends JpaRepository<Usuario, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> buscarConBloqueo(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.email = :email")
    Optional<Usuario> buscarPorEmailConBloqueo(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.localId = :localId AND u.nombreUsuario = :nombreUsuario")
    Optional<Usuario> buscarPorNombreDeUsuarioConBloqueo(@Param("localId") Long localId,
            @Param("nombreUsuario") String nombreUsuario);

    @Query("SELECT COUNT(u) > 0 FROM Usuario u WHERE u.localId = :localId "
            + "AND LOWER(u.nombreUsuario) = LOWER(:nombreUsuario) AND u.id <> :excluirId")
    boolean existeNombreDeUsuario(@Param("localId") Long localId, @Param("nombreUsuario") String nombreUsuario,
            @Param("excluirId") Long excluirId);

    List<Usuario> findByLocalIdAndActivoTrueAndRolCodigoInOrderByNombreAsc(Long localId, Collection<String> codigos);
}
