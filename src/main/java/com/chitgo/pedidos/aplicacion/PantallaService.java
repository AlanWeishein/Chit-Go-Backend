package com.chitgo.pedidos.aplicacion;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.pedidos.dominio.puertos.PedidosDePantalla;
import com.chitgo.pedidos.dominio.puertos.PedidosDePantalla.PedidoVisible;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

@Service
@Transactional(readOnly=true)
public class PantallaService {
    private final LocalRepository locales;
    private final PedidosDePantalla pedidos;
    public PantallaService(LocalRepository locales, PedidosDePantalla pedidos) { this.locales = locales; this.pedidos = pedidos; }

    public PantallaDto consultar(String tokenPantalla) {
        var local = locales.buscarPorTokenPantalla(tokenPantalla)
                .orElseThrow(() -> new NoEncontradoException("La pantalla no está disponible"));
        return new PantallaDto(local.getId(), pedidos.delLocal(local.getId()));
    }

    public AccesoPantalla acceso(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) throw new AccesoDenegadoException("Esta acción es de un local");
        var local = locales.buscarPorId(usuario.localId()).orElseThrow(() -> new NoEncontradoException("El local no existe"));
        return new AccesoPantalla(local.getId(), local.getTokenPantalla());
    }

    public record PantallaDto(Long localId, List<PedidoVisible> pedidos) { }
    public record AccesoPantalla(Long localId, String tokenPantalla) { }
}
