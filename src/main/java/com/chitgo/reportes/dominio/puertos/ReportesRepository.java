package com.chitgo.reportes.dominio.puertos;

import java.math.BigDecimal;
import java.time.Instant;
import com.chitgo.compartido.dominio.PaginaDe;

public interface ReportesRepository {
    Ventas ventas(Long localId, Instant desde, Instant hasta, int page, int size);
    Tiempos tiempos(Long localId, Instant desde, Instant hasta, int page, int size);
    PaginaDe<PlatoPedido> platos(Long localId, Instant desde, Instant hasta, int page, int size);

    record Venta(Long cuentaId, Instant cerradaEn, BigDecimal total) { }
    record Ventas(BigDecimal totalVentas, long cantidadCuentas, PaginaDe<Venta> detalle) { }
    record Tiempo(Long pedidoId, Instant enPreparacionEn, Instant listoEn, long segundos) { }
    record Tiempos(long cantidadPedidos, BigDecimal promedioSegundos, PaginaDe<Tiempo> detalle) { }
    record PlatoPedido(Long platoId, String nombre, long cantidad) { }
}
