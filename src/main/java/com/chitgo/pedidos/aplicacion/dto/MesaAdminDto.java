package com.chitgo.pedidos.aplicacion.dto;

public record MesaAdminDto(Long id, String numero, boolean inhabilitada, String motivoInhabilitacion,
        boolean activa, String tokenQr) { }
