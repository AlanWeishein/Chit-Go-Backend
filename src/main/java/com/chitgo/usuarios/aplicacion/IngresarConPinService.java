package com.chitgo.usuarios.aplicacion;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.ContrasenaRequeridaException;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.PinIncorrectoException;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Login con PIN de Mozo, Caja y Cocina (CU-10, RF-32), con el bloqueo a los 5 intentos (RNF-07). El mozo,
 * además, tiene que haber entrado con usuario y contraseña en ese dispositivo en los últimos 7 días (RF-13).
 */
@Service
public class IngresarConPinService {

    static final String USUARIO_INVALIDO = "El usuario no está en la lista de este local.";

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;
    private final CodificadorDeClaves codificador;
    private final EmisorDeHabilitaciones habilitaciones;
    private final AperturaDeSesion apertura;
    private final Reloj reloj;

    IngresarConPinService(LocalRepository locales, UsuarioRepository usuarios, CodificadorDeClaves codificador,
            EmisorDeHabilitaciones habilitaciones, AperturaDeSesion apertura, Reloj reloj) {
        this.locales = locales;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.habilitaciones = habilitaciones;
        this.apertura = apertura;
        this.reloj = reloj;
    }

    /**
     * El intento fallido se guarda aunque la respuesta sea un error: por eso esas dos excepciones no deshacen
     * la transacción.
     *
     * @throws NoEncontradoException     si el token de staff no es de ningún local, o el usuario no es de ese
     *                                   local, está dado de baja o no entra con PIN
     * @param habilitacion                 el valor de la cookie de dispositivo, o NULL si no vino
     * @throws UsuarioBloqueadoException    si ya estaba bloqueado, o se bloqueó con este intento
     * @throws ContrasenaRequeridaException si es un mozo sin la cookie de dispositivo vigente para él
     * @throws PinIncorrectoException       si el PIN no coincide
     */
    @Transactional(noRollbackFor = {PinIncorrectoException.class, UsuarioBloqueadoException.class})
    public ResultadoDeIngreso ingresar(String tokenStaff, Long usuarioId, String pin, String habilitacion) {
        Local local = locales.buscarPorTokenStaff(tokenStaff)
                .orElseThrow(() -> new NoEncontradoException(ConsultarStaffService.QR_INVALIDO));
        // Uno de otro local da lo mismo que uno que no existe, y no suma intentos: si no, desde un local se
        // podría bloquear a los usuarios de otro.
        Usuario usuario = usuarios.buscarPorIdParaIngreso(usuarioId)
                .filter(u -> local.getId().equals(u.getLocalId()))
                .filter(Usuario::isActivo)
                .filter(u -> u.getRol().entraConPin())
                .orElseThrow(() -> new NoEncontradoException(USUARIO_INVALIDO));

        // Bloqueado, el PIN ni se mira: así el bloqueo no sirve para seguir probando PINes. Va antes que la
        // cookie de dispositivo: si no, un mozo bloqueado y sin cookie iría de P-32 al 401 y nunca vería que
        // tiene que pedirle el blanqueo al Admin. No suma intentos, así que no le da nada al que prueba.
        Instant ahora = reloj.ahora();
        if (usuario.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException();
        }
        // Un mozo sin la cookie de dispositivo vigente para él no llega al PIN, y no suma intentos.
        if (usuario.getRol().esMozo() && !dispositivoHabilitadoPara(usuario, habilitacion)) {
            throw new ContrasenaRequeridaException();
        }
        if (!codificador.verificar(pin, usuario.getPinHash())) {
            usuario.registrarIntentoFallido();
            if (usuario.estaBloqueado(ahora)) {
                throw new UsuarioBloqueadoException();
            }
            throw new PinIncorrectoException();
        }
        usuario.registrarIngreso();
        return apertura.abrir(usuario, local);
    }

    /** La cookie tiene que ser de este mozo: la de otro que entró con contraseña en el mismo celular no sirve. */
    private boolean dispositivoHabilitadoPara(Usuario usuario, String habilitacion) {
        if (habilitacion == null || habilitacion.isBlank()) {
            return false;
        }
        try {
            return usuario.getId().equals(habilitaciones.leer(habilitacion).usuarioId());
        } catch (TokenInvalidoException e) {
            return false;
        }
    }
}
