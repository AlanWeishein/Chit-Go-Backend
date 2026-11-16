package com.chitgo.usuarios.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.SinSesionException;
import com.chitgo.usuarios.dominio.CredencialNuevaInvalidaException;
import com.chitgo.usuarios.dominio.CredencialYaElegidaException;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * El usuario elige su credencial en el primer ingreso o después de un blanqueo (CU-12, RF-32). Hasta que lo
 * hace, SecurityConfig no lo deja hacer otra cosa. Al guardarla se apaga la marca y se emite un token nuevo.
 */
@Service
public class ElegirCredencialService {

    private final UsuarioRepository usuarios;
    private final LocalRepository locales;
    private final CodificadorDeClaves codificador;
    private final AperturaDeSesion apertura;

    ElegirCredencialService(UsuarioRepository usuarios, LocalRepository locales, CodificadorDeClaves codificador,
            AperturaDeSesion apertura) {
        this.usuarios = usuarios;
        this.locales = locales;
        this.codificador = codificador;
        this.apertura = apertura;
    }

    /**
     * Mozo, Caja y Cocina eligen el PIN, y el Admin y el Gerente/Dueño, la contraseña. El mozo puede elegir
     * también la contraseña: en el primer ingreso el front se la pide (P-32b); después de un blanqueo de PIN,
     * no. Cada una tiene que ser distinta de la que tenía.
     *
     * @throws SinSesionException               si el usuario ya no existe o está dado de baja
     * @throws CredencialYaElegidaException      si su credencial no es provisoria
     * @throws CredencialNuevaInvalidaException si falta la que le toca, sobra una que su rol no usa, la
     *                                          repetición no coincide o es igual a la anterior
     */
    @Transactional
    public ResultadoDeIngreso elegir(Long usuarioId, String pin, String pinRepetido, String password,
            String passwordRepetida) {
        // Con la fila bloqueada, como en los logins: el blanqueo de HU-14 toca las mismas columnas.
        Usuario usuario = usuarios.buscarPorIdParaIngreso(usuarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new SinSesionException("El usuario no existe o está dado de baja"));
        // La marca se mira en la base y no en el token: es la que vale si la cambió desde otra pestaña.
        if (!usuario.isDebeCambiarCredencial()) {
            throw new CredencialYaElegidaException();
        }

        Rol rol = usuario.getRol();
        if (rol.entraConPin() && pin == null) {
            throw CredencialNuevaInvalidaException.faltaElPin();
        }
        if (rol.entraConEmail() && password == null) {
            throw CredencialNuevaInvalidaException.faltaLaContrasena();
        }
        boolean pinNoCorresponde = pin != null && !rol.entraConPin();
        boolean contrasenaNoCorresponde = password != null && !rol.entraConEmail() && !rol.esMozo();
        if (pinNoCorresponde || contrasenaNoCorresponde) {
            throw CredencialNuevaInvalidaException.noCorrespondeAlRol();
        }

        String nuevoPinHash = null;
        if (pin != null) {
            if (!pin.equals(pinRepetido)) {
                throw CredencialNuevaInvalidaException.noCoinciden();
            }
            if (codificador.verificar(pin, usuario.getPinHash())) {
                throw CredencialNuevaInvalidaException.pinIgualAlAnterior();
            }
            nuevoPinHash = codificador.hashear(pin);
        }
        String nuevoHashPassword = null;
        if (password != null) {
            if (!password.equals(passwordRepetida)) {
                throw CredencialNuevaInvalidaException.noCoinciden();
            }
            if (codificador.verificar(password, usuario.getHashPassword())) {
                throw CredencialNuevaInvalidaException.contrasenaIgualALaAnterior();
            }
            nuevoHashPassword = codificador.hashear(password);
        }
        usuario.elegirCredencial(nuevoPinHash, nuevoHashPassword);

        // Token nuevo, ya sin la marca. El local sale de la fila del usuario; el Gerente/Dueño no tiene.
        Local local = usuario.getLocalId() != null
                ? locales.buscarPorId(usuario.getLocalId()).orElseThrow()
                : null;
        return apertura.abrir(usuario, local);
    }
}
