package com.chitgo.usuarios.aplicacion;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.CredencialesIncorrectasException;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Login con email y contraseña del Admin y el Gerente/Dueño (CU-11, RF-32), con el bloqueo a los 5 intentos
 * (RNF-07). Toda falla da el mismo error, sin decir si el email existe ni si el usuario está bloqueado.
 */
@Service
public class IngresarConEmailService {

    private final UsuarioRepository usuarios;
    private final LocalRepository locales;
    private final CodificadorDeClaves codificador;
    private final AperturaDeSesion apertura;
    private final Reloj reloj;

    /**
     * Contra este hash se verifica la contraseña cuando no hay un usuario que pueda entrar, para que la
     * respuesta tarde lo mismo que con un email que existe. Es de una clave al azar, que nadie conoce.
     */
    private final String hashDeRelleno;

    IngresarConEmailService(UsuarioRepository usuarios, LocalRepository locales, CodificadorDeClaves codificador,
            AperturaDeSesion apertura, Reloj reloj) {
        this.usuarios = usuarios;
        this.locales = locales;
        this.codificador = codificador;
        this.apertura = apertura;
        this.reloj = reloj;
        this.hashDeRelleno = codificador.hashear(UUID.randomUUID().toString());
    }

    /**
     * El intento fallido se guarda aunque la respuesta sea un error: por eso esa excepción no deshace la
     * transacción.
     *
     * @throws CredencialesIncorrectasException si el email no es de un Admin o Gerente/Dueño activo, si la
     *                                          contraseña no coincide, o si el usuario está bloqueado
     */
    @Transactional(noRollbackFor = CredencialesIncorrectasException.class)
    public ResultadoDeIngreso ingresar(String email, String password) {
        // Un dado de baja, o alguien de otro rol que tenga email, da lo mismo que un email que no existe, y no
        // suma intentos.
        Usuario usuario = usuarios.buscarPorEmailParaIngreso(email.strip())
                .filter(Usuario::isActivo)
                .filter(u -> u.getRol().entraConEmail())
                .orElse(null);
        if (usuario == null) {
            codificador.verificar(password, hashDeRelleno);
            throw new CredencialesIncorrectasException();
        }

        // Con el usuario bloqueado la contraseña también se verifica, para que tarde lo mismo, pero el
        // resultado no se usa: la respuesta es la misma, así que el bloqueo no deja seguir probando.
        boolean coincide = codificador.verificar(password, usuario.getHashPassword());
        if (usuario.estaBloqueado(reloj.ahora())) {
            throw new CredencialesIncorrectasException();
        }
        if (!coincide) {
            usuario.registrarIntentoFallido();
            throw new CredencialesIncorrectasException();
        }
        usuario.registrarIngreso();

        // El local sale de la fila del usuario. El Gerente/Dueño no tiene.
        Local local = usuario.getLocalId() != null
                ? locales.buscarPorId(usuario.getLocalId()).orElseThrow()
                : null;
        return apertura.abrir(usuario, local);
    }
}
