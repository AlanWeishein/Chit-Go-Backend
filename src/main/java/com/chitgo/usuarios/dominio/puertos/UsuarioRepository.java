package com.chitgo.usuarios.dominio.puertos;

import java.time.LocalDate;
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

    /** El email es único en toda la base. También con la fila bloqueada, por el mismo motivo. */
    Optional<Usuario> buscarPorEmailParaIngreso(String email);

    /** El nombre de usuario del mozo es único dentro del local. También con la fila bloqueada. */
    Optional<Usuario> buscarPorNombreDeUsuarioParaIngreso(Long localId, String nombreUsuario);

    Usuario guardar(Usuario usuario);

    /**
     * Si el nombre de usuario ya lo tiene otro usuario del local, sin distinguir mayúsculas y contando a los
     * dados de baja. {@code excluirUsuarioId} deja afuera al propio usuario al editar (puede ser NULL).
     */
    boolean existeNombreDeUsuario(Long localId, String nombreUsuario, Long excluirUsuarioId);

    /**
     * La lista del login con PIN: los activos del local con alguno de esos roles y sin una ausencia que cubra
     * {@code hoy} (hasta NULL = sin fecha de vuelta), ordenados por nombre.
     */
    List<Usuario> listarParaElLogin(Long localId, Collection<String> codigosDeRol, LocalDate hoy);

    /** Si el usuario tiene, como empleado de ese local, una ausencia que cubre {@code hoy}. */
    boolean tieneAusenciaVigente(Long usuarioId, Long localId, LocalDate hoy);

    /** Los Admin del local, también los dados de baja, por nombre y después por id (HU-16). */
    List<Usuario> listarAdminsDelLocal(Long localId);

    /**
     * Un Admin del local, con la fila bloqueada como en el login: desbloquearlo no se cruza con un intento que suma.
     * Vacío si no existe, es de otro rol o es de otro local.
     */
    Optional<Usuario> buscarAdminParaModificar(Long id, Long localId);

    /** Si algún usuario, de cualquier local y rol, ya tiene ese email, sin distinguir mayúsculas. */
    boolean existeEmail(String email);

    /**
     * Como {@link #guardar}, para el alta de un Admin: la unique que puede saltar es la del email, y la traduce a
     * "Ya hay un usuario con ese email".
     */
    Usuario guardarConEmail(Usuario usuario);
}
