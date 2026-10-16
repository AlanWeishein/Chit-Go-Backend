package com.chitgo.usuarios.aplicacion;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.aplicacion.dto.SesionDto.LocalDeSesionDto;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.PinIncorrectoException;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/** Login con PIN de Mozo, Caja y Cocina (CU-10, RF-32), con el bloqueo a los 5 intentos (RNF-07). */
@Service
public class IngresarConPinService {

    static final String USUARIO_INVALIDO = "El usuario no está en la lista de este local.";

    private final LocalRepository locales;
    private final UsuarioRepository usuarios;
    private final CodificadorDeClaves codificador;
    private final EmisorDeTokens emisor;
    private final Reloj reloj;

    public IngresarConPinService(LocalRepository locales, UsuarioRepository usuarios, CodificadorDeClaves codificador,
            EmisorDeTokens emisor, Reloj reloj) {
        this.locales = locales;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    /**
     * El intento fallido se guarda aunque la respuesta sea un error: por eso esas dos excepciones no deshacen
     * la transacción.
     *
     * @throws NoEncontradoException     si el token de staff no es de ningún local, o el usuario no es de ese
     *                                   local, está dado de baja o no entra con PIN
     * @throws PinIncorrectoException    si el PIN no coincide
     * @throws UsuarioBloqueadoException si ya estaba bloqueado, o se bloqueó con este intento
     */
    @Transactional(noRollbackFor = {PinIncorrectoException.class, UsuarioBloqueadoException.class})
    public ResultadoDeIngreso ingresar(String tokenStaff, Long usuarioId, String pin) {
        Local local = locales.buscarPorTokenStaff(tokenStaff)
                .orElseThrow(() -> new NoEncontradoException(ConsultarStaffService.QR_INVALIDO));
        // Uno de otro local da lo mismo que uno que no existe, y no suma intentos: si no, desde un local se
        // podría bloquear a los usuarios de otro.
        Usuario usuario = usuarios.buscarPorIdParaIngreso(usuarioId)
                .filter(u -> local.getId().equals(u.getLocalId()))
                .filter(Usuario::isActivo)
                .filter(u -> u.getRol().entraConPin())
                .orElseThrow(() -> new NoEncontradoException(USUARIO_INVALIDO));

        // Tarea 5: acá, un MOZO sin la cookie de dispositivo vigente (usuario y contraseña cada 7 días) no entra.

        // Bloqueado, el PIN ni se mira: así el bloqueo no sirve para seguir probando PINes.
        Instant ahora = reloj.ahora();
        if (usuario.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException();
        }
        if (!codificador.verificar(pin, usuario.getPinHash())) {
            usuario.registrarIntentoFallido();
            if (usuario.estaBloqueado(ahora)) {
                throw new UsuarioBloqueadoException();
            }
            throw new PinIncorrectoException();
        }
        usuario.registrarIngreso();

        // Tarea 7: T según el rol (Caja, 60 minutos). Por ahora, el del local para todos.
        Duration duracion = Duration.ofMinutes(local.getMinutosInactividadSesion());
        DatosDeSesion datos = new DatosDeSesion(usuario.getId(), usuario.getRol().getCodigo(), local.getId(),
                usuario.isDebeCambiarCredencial());
        String token = emisor.emitir(datos, duracion);

        // El vencimiento tal como quedó en el token (en segundos), el mismo que va a dar /auth/sesion.
        Instant expiraEn = emisor.leer(token).expiraEn();
        SesionDto sesion = new SesionDto(usuario.getId(), usuario.getNombre(), datos.rol(),
                new LocalDeSesionDto(local.getId(), local.getNombre()), datos.debeCambiarCredencial(), expiraEn);
        return new ResultadoDeIngreso(token, duracion, sesion);
    }
}
