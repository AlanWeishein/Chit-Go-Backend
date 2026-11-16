package com.chitgo.reportes.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chitgo.compartido.aplicacion.*;
import com.chitgo.reportes.dominio.puertos.ReportesRepository;
import com.chitgo.reportes.dominio.puertos.ReportesRepository.*;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@Service
@Transactional(readOnly = true)
public class ConsultarReportesService {
    private final ReportesRepository reportes;
    private final LocalDeConsulta locales;
    private final ReglasDelNegocio reglas;
    public ConsultarReportesService(ReportesRepository reportes, LocalDeConsulta locales, ReglasDelNegocio reglas) {
        this.reportes = reportes;
        this.locales = locales;
        this.reglas = reglas;
    }

    public VentasDto ventas(UsuarioAutenticado usuario, Long localId, LocalDate desde, LocalDate hasta, int page, int size) {
        PaginaDto.validar(page, size);
        var periodo = PeriodoDeConsulta.de(desde, hasta, reglas.zonaHoraria(), true);
        var datos = reportes.ventas(locales.resolver(usuario, localId), periodo.desde(), periodo.hastaExclusivo(), page, size);
        return new VentasDto(desde, hasta, datos.totalVentas(), datos.cantidadCuentas(),
                PaginaDto.de(datos.detalle().contenido(), page, size, datos.detalle().total()));
    }

    public TiemposDto tiempos(UsuarioAutenticado usuario, Long localId, LocalDate desde, LocalDate hasta, int page, int size) {
        PaginaDto.validar(page, size);
        var periodo = PeriodoDeConsulta.de(desde, hasta, reglas.zonaHoraria(), true);
        var datos = reportes.tiempos(locales.resolver(usuario, localId), periodo.desde(), periodo.hastaExclusivo(), page, size);
        return new TiemposDto(desde, hasta, datos.cantidadPedidos(), datos.promedioSegundos(),
                PaginaDto.de(datos.detalle().contenido(), page, size, datos.detalle().total()));
    }

    public PaginaDto<PlatoPedido> platos(UsuarioAutenticado usuario, Long localId, LocalDate desde, LocalDate hasta,
            int page, int size) {
        PaginaDto.validar(page, size);
        var periodo = PeriodoDeConsulta.de(desde, hasta, reglas.zonaHoraria(), true);
        var datos = reportes.platos(locales.resolver(usuario, localId), periodo.desde(), periodo.hastaExclusivo(), page, size);
        return PaginaDto.de(datos.contenido(), page, size, datos.total());
    }

    public record VentasDto(LocalDate desde, LocalDate hasta, BigDecimal totalVentas, long cantidadCuentas,
            PaginaDto<Venta> detalle) { }
    public record TiemposDto(LocalDate desde, LocalDate hasta, long cantidadPedidos, BigDecimal promedioSegundos,
            PaginaDto<Tiempo> detalle) { }
}
