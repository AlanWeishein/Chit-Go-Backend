package com.chitgo.pedidos.dominio.puertos;

import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.TipoAviso;
import java.util.List;
import java.util.Optional;

public interface AvisoRepository {

    Aviso guardar(Aviso aviso);

    boolean hayPendiente(Long cuentaId, TipoAviso tipo);

    Optional<Aviso> buscarPorId(Long id, Long localId);

    List<Aviso> listarDelLocal(Long localId, boolean pendientes);
}
