package com.chitgo.usuarios.aplicacion;

import java.util.stream.Collectors;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeSuperadmin;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/** Operaciones locales del perfil script; no se exponen por HTTP. */
@Service
public class SuperadminOperativoService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final CodificadorDeClaves claves;
    private final Validator validador;
    private final Reloj reloj;

    public SuperadminOperativoService(UsuarioRepository usuarios, RolRepository roles, CodificadorDeClaves claves,
            Validator validador, Reloj reloj) {
        this.usuarios = usuarios; this.roles = roles; this.claves = claves;
        this.validador = validador; this.reloj = reloj;
    }

    @Transactional
    public OperadorCreado crear(DatosDeAltaDeSuperadmin datos) {
        String errores = validador.validate(datos).stream().map(v -> v.getMessage()).sorted().distinct()
                .collect(Collectors.joining(". "));
        if (!errores.isEmpty()) throw new SolicitudInvalidaException(errores);
        if (usuarios.existeEmail(datos.email())) throw new ReglaDeNegocioException("Ya hay un usuario con ese email");
        var rol = roles.buscarPorCodigo("SUPERADMIN").orElseThrow();
        Usuario operador = usuarios.guardarConEmail(Usuario.nuevoSuperadmin(rol, datos.nombre(), datos.email(),
                claves.hashear(datos.password())));
        return new OperadorCreado(operador.getId(), operador.getEmail());
    }

    /** Solo levanta RNF-07. No cambia contraseña, marcas de primer ingreso ni estado activo. */
    @Transactional
    public boolean desbloquear(String email) {
        if (email == null || email.isBlank() || email.length() > 160) {
            throw new SolicitudInvalidaException("Indicá el email del SuperAdmin");
        }
        Usuario operador = usuarios.buscarPorEmailParaIngreso(email.strip())
                .filter(u -> "SUPERADMIN".equals(u.getRol().getCodigo()) && u.getLocalId() == null)
                .orElseThrow(() -> new ReglaDeNegocioException("El SuperAdmin no existe"));
        if (!operador.isActivo()) throw new ReglaDeNegocioException("El SuperAdmin está dado de baja");
        boolean cambio = operador.desbloquear(reloj.ahora());
        if (cambio) usuarios.guardar(operador);
        return cambio;
    }

    public record OperadorCreado(Long id, String email) { }
}
