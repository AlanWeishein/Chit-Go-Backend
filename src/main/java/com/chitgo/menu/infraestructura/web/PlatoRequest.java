package com.chitgo.menu.infraestructura.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.chitgo.menu.aplicacion.dto.DatosDePlato;

/**
 * El cuerpo de POST /admin/platos y de PUT /admin/platos/{id}. Acá van solo los largos del esquema, con el 400
 * genérico. Sin nombre, el precio y la imagen los valida Plato, con los mensajes de HU-12: ManejadorDeErrores
 * pisa el mensaje de un {@code @Valid} que falla.
 */
public record PlatoRequest(@Size(max = 120) String nombre, @Size(max = 500) String descripcion,
                           @Digits(integer = 8, fraction = 2) BigDecimal precio, @NotNull Long categoriaId,
                           String imagenUrl, List<@NotNull Long> etiquetaIds) {

    DatosDePlato aDatos() {
        return new DatosDePlato(nombre, descripcion, precio, categoriaId, imagenUrl, etiquetaIds);
    }
}
