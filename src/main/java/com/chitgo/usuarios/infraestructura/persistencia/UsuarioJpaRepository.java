package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.LocalDate;
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

    /** Una sola consulta: la ausencia va en un NOT EXISTS, no una por usuario. */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.localId = :localId AND u.activo = TRUE AND u.rol.codigo IN :codigos
              AND NOT EXISTS (SELECT a.id FROM Ausencia a WHERE a.empleado.usuario = u AND a.localId = :localId
                              AND a.desde <= :hoy AND (a.hasta IS NULL OR a.hasta >= :hoy))
            ORDER BY u.nombre ASC, u.id ASC
            """)
    List<Usuario> listarParaElLogin(@Param("localId") Long localId, @Param("codigos") Collection<String> codigos,
            @Param("hoy") LocalDate hoy);

    @Query("""
            SELECT COUNT(a) > 0 FROM Ausencia a
            WHERE a.empleado.usuario.id = :usuarioId AND a.localId = :localId
              AND a.desde <= :hoy AND (a.hasta IS NULL OR a.hasta >= :hoy)
            """)
    boolean tieneAusenciaVigente(@Param("usuarioId") Long usuarioId, @Param("localId") Long localId,
            @Param("hoy") LocalDate hoy);
}
