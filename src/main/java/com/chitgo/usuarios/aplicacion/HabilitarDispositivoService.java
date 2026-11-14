package com.chitgo.usuarios.aplicacion;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.aplicacion.dto.DispositivoDto;
import com.chitgo.usuarios.dominio.DispositivoHabilitado;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;
import com.chitgo.usuarios.dominio.UsuarioOContrasenaIncorrectosException;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Usuario y contraseña del mozo, la primera vez y cada 7 días (RF-13, P-32). No abre la sesión: habilita el
 * dispositivo para que después entre con el PIN. Los fallos suman intentos y bloquean a los 5 (RNF-07), con el
 * mismo contador que el PIN.
 */
@Service
public class HabilitarDispositivoService {

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;
    private final CodificadorDeClaves codificador;
    private final EmisorDeHabilitaciones emisor;
    private final Reloj reloj;

    /** Como en el login con email: para que un usuario que no existe tarde lo mismo que uno que sí. */
    private final String hashDeRelleno;

    HabilitarDispositivoService(LocalRepository locales, UsuarioRepository usuarios, CodificadorDeClaves codificador,
            EmisorDeHabilitaciones emisor, Reloj reloj) {
        this.locales = locales;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
        this.reloj = reloj;
        this.hashDeRelleno = codificador.hashear(UUID.randomUUID().toString());
    }

    /**
     * El intento fallido se guarda aunque la respuesta sea un error: por eso esa excepción no deshace la
     * transacción.
     *
     * @throws NoEncontradoException                  si el token de staff no es de ningún local
     * @throws UsuarioOContrasenaIncorrectosException si el usuario no es un mozo activo de ese local, si tiene una
     *                                                ausencia que cubre hoy, o si la contraseña no coincide, esté
     *                                                bloqueado o no
     * @throws UsuarioBloqueadoException              si está bloqueado y la contraseña es la correcta (CP-10-12):
     *                                                no deja la cookie de dispositivo
     */
    @Transactional(noRollbackFor = UsuarioOContrasenaIncorrectosException.class)
    public ResultadoDeHabilitacion habilitar(String tokenStaff, String nombreUsuario, String password) {
        Local local = locales.buscarPorTokenStaff(tokenStaff)
                .orElseThrow(() -> new NoEncontradoException(ConsultarStaffService.QR_INVALIDO));
        // El nombre de usuario es único dentro del local del QR. Un dado de baja, alguien de otro rol o un mozo con
        // una ausencia vigente (decidido el 20/10, punto 11) da lo mismo que uno que no existe, y no suma intentos.
        Usuario usuario = usuarios.buscarPorNombreDeUsuarioParaIngreso(local.getId(), nombreUsuario.strip())
                .filter(Usuario::isActivo)
                .filter(u -> u.getRol().esMozo())
                .filter(u -> !usuarios.tieneAusenciaVigente(u.getId(), local.getId(), DiaDelLocal.hoy(reloj)))
                .orElse(null);
        if (usuario == null) {
            codificador.verificar(password, hashDeRelleno);
            throw new UsuarioOContrasenaIncorrectosException();
        }

        // La contraseña se verifica siempre, también bloqueado, así los tres casos tardan lo mismo (CP-11-04).
        boolean coincide = codificador.verificar(password, usuario.getHashPassword());
        if (usuario.estaBloqueado(reloj.ahora())) {
            // 423 solo a quien prueba que sabe la contraseña (B', decidido el 20/10, punto 4): el resto no se
            // entera de que el usuario existe y está bloqueado. Ninguno de los dos suma intentos.
            if (coincide) {
                throw new UsuarioBloqueadoException();
            }
            throw new UsuarioOContrasenaIncorrectosException();
        }
        if (!coincide) {
            usuario.registrarIntentoFallido();
            throw new UsuarioOContrasenaIncorrectosException();
        }
        usuario.registrarIngreso();

        String token = emisor.emitir(usuario.getId());
        // El vencimiento tal como quedó en el token, el mismo que va a dar /auth/dispositivo.
        DispositivoHabilitado habilitado = emisor.leer(token);
        return new ResultadoDeHabilitacion(token,
                new DispositivoDto(usuario.getId(), usuario.getNombre(), habilitado.venceEn()));
    }
}
