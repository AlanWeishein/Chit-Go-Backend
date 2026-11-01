package com.chitgo.pedidos.infraestructura.websocket;

import com.chitgo.pedidos.dominio.EventoTiempoReal;

public record AvisoParaPush(Long localId, EventoTiempoReal evento) {}
