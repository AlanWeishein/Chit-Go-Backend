package com.chitgo.auditoria.aplicacion;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.auditoria.dominio.puertos.ConsultaDeAuditoria;
import com.chitgo.auditoria.dominio.puertos.ConsultaDeAuditoria.Fila;
import com.chitgo.compartido.aplicacion.*;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@Service
public class ConsultarAuditoriaService {
    private final ConsultaDeAuditoria auditoria;
    private final LocalDeConsulta locales;
    private final ReglasDelNegocio reglas;
    public ConsultarAuditoriaService(ConsultaDeAuditoria auditoria, LocalDeConsulta locales, ReglasDelNegocio reglas) {
        this.auditoria = auditoria;
        this.locales = locales;
        this.reglas = reglas;
    }

    @Transactional(readOnly=true)
    public PaginaDto<Fila> consultar(UsuarioAutenticado usuario, Long localId, String entidad, String accion,
            Long usuarioId, LocalDate desde, LocalDate hasta, int page, int size) {
        PaginaDto.validar(page, size);
        var periodo = PeriodoDeConsulta.de(desde, hasta, reglas.zonaHoraria(), false);
        if ((entidad != null && entidad.length() > 60) || (accion != null && accion.length() > 60)
                || (usuarioId != null && usuarioId < 1)) throw new SolicitudInvalidaException("El filtro no es válido");
        var datos = auditoria.consultar(locales.resolver(usuario, localId), entidad, accion, usuarioId,
                periodo.desde(), periodo.hastaExclusivo(), page, size);
        return PaginaDto.de(datos.contenido(), page, size, datos.total());
    }
}
