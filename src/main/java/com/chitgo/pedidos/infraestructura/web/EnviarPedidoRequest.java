package com.chitgo.pedidos.infraestructura.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El cuerpo de POST /public/pedidos. El uuidCliente lo genera la app en cada envío (RNF-09). */
public record EnviarPedidoRequest(@NotNull UUID uuidCliente, @NotEmpty List<@Valid ItemRequest> items) {

    public record ItemRequest(@NotNull Long platoId, @Min(1) @Max(999) int cantidad, @Size(max = 200) String observacion) {
    }
}
