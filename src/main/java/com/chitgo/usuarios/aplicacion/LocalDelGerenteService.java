package com.chitgo.usuarios.aplicacion;

import org.springframework.stereotype.Service;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

/**
 * HU-16 (RF-29, RF-27): el local de los reportes y la auditoría del Gerente/Dueño. Solo el Gerente/Dueño: el Admin,
 * Caja y Mozo reciben 403 aunque tengan el permiso. El local es el que lo tiene como propietario. Si el pedido manda
 * un {@code localId} que no es ese, o no tiene local, da 404 "El local no existe", el mismo texto para los dos casos
 * (RNF-10).
 */
@Service
public class LocalDelGerenteService {

    static final String ROL_GERENTE = "GERENTE_DUENO";
    static final String SOLO_GERENTE = "Esta acción es del Gerente/Dueño";
    static final String LOCAL_NO_EXISTE = "El local no existe";

    private final LocalRepository locales;

    public LocalDelGerenteService(LocalRepository locales) {
        this.locales = locales;
    }

    /** @param localIdDelPedido opcional: si viene, tiene que ser el del propietario */
    public Long resolver(UsuarioAutenticado usuario, Long localIdDelPedido) {
        if (!ROL_GERENTE.equals(usuario.rol())) {
            throw new AccesoDenegadoException(SOLO_GERENTE);
        }
        Long propio = locales.buscarPorPropietario(usuario.usuarioId()).map(Local::getId)
                .orElseThrow(() -> new NoEncontradoException(LOCAL_NO_EXISTE));
        if (localIdDelPedido != null && !localIdDelPedido.equals(propio)) {
            throw new NoEncontradoException(LOCAL_NO_EXISTE);
        }
        return propio;
    }
}
