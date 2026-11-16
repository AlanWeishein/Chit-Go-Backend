package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.chitgo.compartido.dominio.PaginaDe;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;

import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;

@Repository
class MesaRepositoryAdapter implements MesaRepository {

    private final MesaJpaRepository jpa;

    MesaRepositoryAdapter(MesaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Mesa> buscarPorTokenQr(String tokenQr) {
        return jpa.findByTokenQrAndActivaTrue(tokenQr);
    }

    @Override
    public Optional<Mesa> buscarPorTokenQrParaAbrirCuenta(String tokenQr) {
        return jpa.buscarParaAbrirCuenta(tokenQr);
    }

    @Override
    public List<Mesa> listarDelLocal(Long localId) {
        return jpa.findByLocalIdAndActivaTrueOrderByNumeroAscIdAsc(localId);
    }

    @Override
    public Optional<Mesa> buscarPorId(Long id, Long localId) { return jpa.findByIdAndLocalId(id, localId); }

    @Override
    public Optional<Mesa> buscarPorIdParaGestionar(Long id, Long localId) { return jpa.paraGestionar(id, localId); }

    @Override
    public PaginaDe<Mesa> pagina(Long localId, int page, int size) {
        var resultado = jpa.findByLocalIdAndActivaTrue(localId, PageRequest.of(page, size, Sort.by("numero", "id")));
        return new PaginaDe<>(resultado.getContent(), resultado.getTotalElements());
    }

    @Override
    public Mesa guardar(Mesa mesa) {
        try {
            return jpa.saveAndFlush(mesa);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaDeNegocioException("Ya existe la mesa " + mesa.getNumero());
        }
    }
}
