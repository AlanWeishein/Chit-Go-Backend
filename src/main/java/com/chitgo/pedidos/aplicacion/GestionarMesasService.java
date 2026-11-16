package com.chitgo.pedidos.aplicacion;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.aplicacion.PaginaDto;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.pedidos.aplicacion.dto.MesaAdminDto;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@Service
public class GestionarMesasService {
    private final MesaRepository mesas;
    private final CuentaRepository cuentas;
    private final Auditor auditor;

    public GestionarMesasService(MesaRepository mesas, CuentaRepository cuentas, Auditor auditor) {
        this.mesas = mesas;
        this.cuentas = cuentas;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    public PaginaDto<MesaAdminDto> listar(UsuarioAutenticado usuario, int page, int size) {
        PaginaDto.validar(page, size);
        var pagina = mesas.pagina(localDe(usuario), page, size);
        return PaginaDto.de(pagina.contenido().stream().map(GestionarMesasService::dto).toList(), page, size,
                pagina.total());
    }

    @Transactional(readOnly = true)
    public MesaAdminDto detalle(UsuarioAutenticado usuario, Long id) {
        return dto(mesas.buscarPorId(id, localDe(usuario)).filter(Mesa::isActiva)
                .orElseThrow(() -> new NoEncontradoException("La mesa no existe")));
    }

    @Transactional
    public MesaAdminDto crear(UsuarioAutenticado usuario, String numero) {
        Mesa mesa = mesas.guardar(new Mesa(localDe(usuario), numero, UUID.randomUUID().toString()));
        registrar(usuario, "CREAR_MESA", mesa, null, dto(mesa));
        return dto(mesa);
    }

    @Transactional
    public MesaAdminDto editar(UsuarioAutenticado usuario, Long id, String numero) {
        Mesa mesa = gestionar(usuario, id);
        MesaAdminDto antes = dto(mesa);
        mesa.renombrar(numero);
        if (!antes.numero().equals(mesa.getNumero())) {
            mesas.guardar(mesa);
            registrar(usuario, "EDITAR_MESA", mesa, antes, dto(mesa));
        }
        return dto(mesa);
    }

    @Transactional
    public MesaAdminDto disponibilidad(UsuarioAutenticado usuario, Long id, boolean inhabilitada, String motivo) {
        Mesa mesa = gestionar(usuario, id);
        MesaAdminDto antes = dto(mesa);
        if (inhabilitada) exigirLibre(mesa);
        mesa.cambiarDisponibilidad(inhabilitada, motivo);
        if (!antes.equals(dto(mesa))) {
            mesas.guardar(mesa);
            registrar(usuario, inhabilitada ? "INHABILITAR_MESA" : "HABILITAR_MESA", mesa, antes, dto(mesa));
        }
        return dto(mesa);
    }

    @Transactional
    public void darDeBaja(UsuarioAutenticado usuario, Long id) {
        Mesa mesa = mesas.buscarPorIdParaGestionar(id, localDe(usuario))
                .orElseThrow(() -> new NoEncontradoException("La mesa no existe"));
        if (!mesa.isActiva()) return;
        exigirLibre(mesa);
        MesaAdminDto antes = dto(mesa);
        mesa.darDeBaja();
        mesas.guardar(mesa);
        registrar(usuario, "BAJA_MESA", mesa, antes, dto(mesa));
    }

    private Mesa gestionar(UsuarioAutenticado usuario, Long id) {
        return mesas.buscarPorIdParaGestionar(id, localDe(usuario)).filter(Mesa::isActiva)
                .orElseThrow(() -> new NoEncontradoException("La mesa no existe"));
    }

    private void exigirLibre(Mesa mesa) {
        if (cuentas.buscarNoCerradaDeMesa(mesa.getId()).isPresent()) {
            throw new ReglaDeNegocioException("Cerrá la cuenta antes de inhabilitar o dar de baja la mesa");
        }
    }

    private void registrar(UsuarioAutenticado usuario, String accion, Mesa mesa, MesaAdminDto antes, MesaAdminDto despues) {
        // El token sirve para el QR del Admin, pero no se copia al historial de auditoría.
        auditor.registrar(mesa.getLocalId(), usuario.usuarioId(), accion, "MESA", mesa.getId(),
                sinToken(antes), sinToken(despues));
    }

    private static Object sinToken(MesaAdminDto mesa) {
        if (mesa == null) return null;
        return new Cambio(mesa.numero(), mesa.inhabilitada(), mesa.motivoInhabilitacion(), mesa.activa());
    }

    private record Cambio(String numero, boolean inhabilitada, String motivoInhabilitacion, boolean activa) { }

    private static MesaAdminDto dto(Mesa mesa) {
        return new MesaAdminDto(mesa.getId(), mesa.getNumero(), mesa.isInhabilitada(),
                mesa.getMotivoInhabilitacion(), mesa.isActiva(), mesa.getTokenQr());
    }

    private static Long localDe(UsuarioAutenticado usuario) {
        if (usuario.localId() == null) throw new AccesoDenegadoException("Esta acción es de un local");
        return usuario.localId();
    }
}
